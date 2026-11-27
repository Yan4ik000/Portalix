package dev.portalix.client.portal.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.portalix.PortalixMod;
import dev.portalix.api.PortalEndpoint;
import dev.portalix.client.compat.ShaderBackend;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.render.PortalDraw;
import dev.portalix.item.PortalixVariant;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public record PortalMarker(PortalEndpoint endpoint, Identifier texture, Vec3 camera, Frame frame)
    implements PortalSurfaceRenderer.Node {
  public static final class Frame {
    boolean submitted;
    GpuTextureView portalDepth;

    public PortalMarker marker(PortalEndpoint endpoint, Identifier texture, Vec3 camera) {
      submitted = true;
      return new PortalMarker(endpoint, texture, camera, this);
    }
  }

  private static final RenderPipeline PIPELINE = pipeline("portal_marker", false, false);
  private static final RenderPipeline IRIS_PIPELINE = pipeline("portal_marker_iris_hand", true, false);
  private static final RenderPipeline MASKED_PIPELINE = pipeline("portal_marker_with_occlusion", false, true);
  private static final RenderPipeline IRIS_MASKED_PIPELINE =
      pipeline("portal_marker_iris_hand_with_occlusion", true, true);

  private static RenderPipeline pipeline(String name, boolean iris, boolean occlusion) {
    var builder = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
        .withLocation(PortalixMod.id("pipeline/" + name))
        .withVertexShader("core/position_tex")
        .withFragmentShader(PortalixMod.id("core/portal_marker"))
        .withSampler("Sampler0")
        .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN, false))
        .withCull(false);
    if (iris)
      builder.withShaderDefine("PORTALIX_IRIS_HAND").withSampler("PortalWorldDepth").withSampler("PortalHandDepth");
    if (occlusion)
      builder.withShaderDefine("PORTALIX_MARKER_OCCLUSION").withSampler("PortalSurfaceDepth");
    return RenderPipelines.register(builder.build());
  }

  public static Identifier texture(PortalixVariant variant, boolean secondary) {
    return PortalixMod.id("textures/portals/markers/" + variant.palette()
        + (secondary ? "_secondary.png" : "_primary.png"));
  }

  PortalDraw prepare() {
    var image = Minecraft.getInstance().getTextureManager().getTexture(texture);
    var binding = new PortalDraw.Texture("Sampler0", image.getTextureView(),
        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
    var depth = IrisShaders.active() ? ShaderBackend.INSTANCE.portalDepthTextures(false) : null;
    var transforms = RenderSystem.getDynamicUniforms().writeTransform(
        RenderSystem.getModelViewMatrix(), new Vector4f(1), new Vector3f(), new Matrix4f());
    return new PortalDraw(depth == null ? PIPELINE : IRIS_PIPELINE, OutputTarget.MAIN_TARGET, transforms,
        PortalDraw.scissor(), depth == null ? List.of(binding) : List.of(binding,
            new PortalDraw.Texture("PortalWorldDepth", depth.world().textureView(), depth.world().sampler()),
            new PortalDraw.Texture("PortalHandDepth", depth.hand().textureView(), depth.hand().sampler())));
  }

  static boolean isMarker(RenderPipeline pipeline) {
    return pipeline == PIPELINE || pipeline == IRIS_PIPELINE;
  }

  static void drawWithOcclusion(PortalDraw marker, PortalDraw.Geometry geometry, GpuTextureView portalDepth) {
    var textures = new ArrayList<>(marker.textures());
    textures.add(new PortalDraw.Texture("PortalSurfaceDepth", portalDepth,
        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)));
    var pipeline = marker.pipeline() == PIPELINE ? MASKED_PIPELINE : IRIS_MASKED_PIPELINE;
    new PortalDraw(pipeline, marker.outputTarget(), marker.dynamicTransforms(),
        marker.scissorState(), textures).draw(geometry);
  }

  void buildGeometry(VertexConsumer vertices) {
    var origin = camera.subtract(endpoint.normal().scale(PortalSurfaceMesh.NORMAL_OFFSET));
    for (int index = 0; index < 4; index++) {
      var vertex = PortalSurfaceMesh.corner(endpoint, origin, index);
      var point = vertex.point();
      vertices.addVertex((float) point.x, (float) point.y, (float) point.z).setUv(vertex.u(), vertex.v());
    }
  }
}
