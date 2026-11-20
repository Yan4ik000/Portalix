package dev.portalix.client.portal.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.portalix.PortalixMod;
import dev.portalix.api.PortalEndpoint;
import dev.portalix.client.compat.ShaderBackend;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.item.PortalixVariant;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

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
    var builder = RenderPipeline.builder()
        .withLocation(PortalixMod.id("pipeline/" + name))
        .withVertexShader("core/position_tex")
        .withFragmentShader(PortalixMod.id("core/portal_marker"))
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        // Minecraft uses reversed depth. Only draw the fragments behind existing geometry.
        .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN, false))
        .withCull(false);
    if (iris)
      builder.withShaderDefine("PORTALIX_IRIS_HAND").withBindGroupLayout(BindGroupLayout.builder()
          .withUniform("PortalWorldDepth", UniformType.COMBINED_IMAGE_SAMPLER)
          .withUniform("PortalHandDepth", UniformType.COMBINED_IMAGE_SAMPLER).build());
    if (occlusion)
      builder.withShaderDefine("PORTALIX_MARKER_OCCLUSION").withBindGroupLayout(
          BindGroupLayout.builder().withUniform("PortalSurfaceDepth", UniformType.COMBINED_IMAGE_SAMPLER).build());
    return RenderPipelines.register(builder.build());
  }

  public static Identifier texture(PortalixVariant variant, boolean secondary) {
    return PortalixMod.id("textures/portals/markers/" + variant.palette()
        + (secondary ? "_secondary.png" : "_primary.png"));
  }

  PreparedRenderType prepare(FeatureFrameContext context) {
    var image = context.textureManager().getTexture(texture);
    var binding = new PreparedRenderType.Texture("Sampler0", image.getTextureView(),
        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
    var depth = IrisShaders.active() ? ShaderBackend.INSTANCE.portalDepthTextures(false) : null;
    return new PreparedRenderType("Portal marker", depth == null ? PIPELINE : IRIS_PIPELINE, null,
        RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy()),
        new ScissorState(RenderSystem.getScissorStateForRenderTypeDraws()),
        depth == null ? List.of(binding) : List.of(binding, depth.world(), depth.hand()));
  }

  static boolean isMarker(RenderPipeline pipeline) {
    return pipeline == PIPELINE || pipeline == IRIS_PIPELINE;
  }

  static PreparedRenderType withOcclusion(PreparedRenderType marker, GpuTextureView portalDepth) {
    var textures = new ArrayList<>(marker.textures());
    textures.add(new PreparedRenderType.Texture("PortalSurfaceDepth", portalDepth,
        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)));
    var pipeline = marker.pipeline() == PIPELINE ? MASKED_PIPELINE : IRIS_MASKED_PIPELINE;
    return new PreparedRenderType(marker.name(), pipeline, null, marker.dynamicTransforms(),
        marker.scissorState(), textures);
  }

  StagedVertexBuffer.Draw appendGeometry(StagedVertexBuffer staged) {
    var draw = staged.appendDraw(DefaultVertexFormat.POSITION_TEX, PrimitiveTopology.QUADS);
    var vertices = staged.getVertexBuilder(draw);
    var origin = camera.subtract(endpoint.normal().scale(PortalSurfaceMesh.NORMAL_OFFSET));
    for (int index = 0; index < 4; index++) {
      var vertex = PortalSurfaceMesh.corner(endpoint, origin, index);
      var point = vertex.point();
      vertices.addVertex((float) point.x, (float) point.y, (float) point.z).setUv(vertex.u(), vertex.v());
    }
    return draw;
  }
}
