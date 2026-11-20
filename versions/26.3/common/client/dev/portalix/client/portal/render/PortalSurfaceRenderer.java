package dev.portalix.client.portal.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass.RenderArea;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.portalix.PortalixMod;
import dev.portalix.api.PortalEndpoint;
import dev.portalix.client.ModResourceReload;
import dev.portalix.client.compat.ShaderBackend;
import dev.portalix.client.compat.iris.IrisEffectTarget;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.particle.PortalParticleMesh;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.item.PortalixVariant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class PortalSurfaceRenderer implements FeatureRenderer<PortalSurfaceRenderer.Node> {
  public static final FeatureRendererType<Node> TYPE =
      FeatureRendererType.create("portalix_portal");
  private static final RenderPipeline PIPELINE =
      RenderPipelines.register(surfacePipeline("portal").build());
  private static final RenderPipeline IRIS_HAND_PIPELINE =
      RenderPipelines.register(handSurfacePipeline("portal_iris_hand").build());
  private static final RenderPipeline MARKER_OCCLUSION_PIPELINE =
      RenderPipelines.register(markerOcclusionPipeline(surfacePipeline("portal_with_marker_occlusion")));
  private static final RenderPipeline IRIS_MARKER_OCCLUSION_PIPELINE =
      RenderPipelines.register(
          markerOcclusionPipeline(handSurfacePipeline("portal_iris_hand_with_marker_occlusion")));
  private static final RenderPipeline APERTURE_PIPELINE =
      RenderPipelines.register(
          portalPipeline(
                  "portal_aperture",
                  RenderPipeline.builder().withBindGroupLayout(BindGroupLayouts.PROJECTION)
                      .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS))
              .withShaderDefine("PORTALIX_IRIS_HAND")
              .withShaderDefine("PORTALIX_DEPTH_APERTURE")
              .withBindGroupLayout(
                  BindGroupLayout.builder()
                      .withUniform("PortalOcclusionDepth", UniformType.COMBINED_IMAGE_SAMPLER).build())
              .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, true))
              .withColorTargetState(
                  new ColorTargetState(
                      Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_NONE))
              .build());

  private static RenderPipeline.Builder portalPipeline(String name, RenderPipeline.Builder builder) {
    return builder
        .withLocation(PortalixMod.id("pipeline/" + name))
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
        .withVertexShader(PortalixMod.id("core/portal"))
        .withFragmentShader(PortalixMod.id("core/portal"))
        .withCull(false)
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
  }

  private static RenderPipeline.Builder surfacePipeline(String name) {
    return portalPipeline(name, RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET))
        .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER2)
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform("PortalScene", UniformType.COMBINED_IMAGE_SAMPLER).build())
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false));
  }

  private static RenderPipeline.Builder handSurfacePipeline(String name) {
    return surfacePipeline(name)
        .withShaderDefine("PORTALIX_IRIS_HAND")
        .withBindGroupLayout(
            BindGroupLayout.builder()
                .withUniform("PortalWorldDepth", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("PortalHandDepth", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("PortalOcclusionDepth", UniformType.COMBINED_IMAGE_SAMPLER)
                .build());
  }

  private static RenderPipeline markerOcclusionPipeline(RenderPipeline.Builder builder) {
    return builder
        .withShaderDefine("PORTALIX_MARKER_OCCLUSION")
        .withColorTargetState(1, new ColorTargetState(
            Optional.of(BlendFunction.TRANSLUCENT), GpuFormat.R32_FLOAT, ColorTargetState.WRITE_ALL))
        .build();
  }

  private final Map<RenderTarget, Background> backgrounds = new IdentityHashMap<>();
  private GpuTextureView markerDepth;
  private final Map<Object, ParticleGeometry> particleGeometry = new IdentityHashMap<>();
  private final List<GpuBuffer> particleBuffers = new ArrayList<>();
  private final ByteBufferBuilder particleStorage = new ByteBufferBuilder(4096);
  private static final Set<PortalSurfaceRenderer> INSTANCES =
      Collections.newSetFromMap(new WeakHashMap<>());

  public PortalSurfaceRenderer() {
    INSTANCES.add(this);
  }

  public static void clearResources() {
    if (ShaderBackend.INSTANCE != null) ShaderBackend.INSTANCE.clearComposite();
    IrisEffectTarget.clear();
    for (PortalSurfaceRenderer renderer : INSTANCES) {
      renderer.releaseBackgrounds();
      renderer.releaseMarkerDepth();
      renderer.particleGeometry.values().forEach(ParticleGeometry::close);
      renderer.particleGeometry.clear();
      renderer.particleBuffers.forEach(GpuBuffer::close);
      renderer.particleBuffers.clear();
    }
  }

  public static void endFrame() {
    IrisEffectTarget.endFrame();
    for (PortalSurfaceRenderer renderer : INSTANCES) {
      renderer
          .backgrounds
          .entrySet()
          .removeIf(
              entry -> {
                if (entry.getKey().getColorTexture() == entry.getValue().source) return false;
                entry.getValue().close();
                return true;
              });
      renderer
          .particleGeometry
          .values()
          .removeIf(
              geometry -> {
                if (geometry.used) {
                  geometry.used = false;
                  return false;
                }
                geometry.close();
                return true;
              });
    }
  }

  private void releaseBackgrounds() {
    for (Background background : backgrounds.values()) background.close();
    backgrounds.clear();
  }

  private void releaseMarkerDepth() {
    if (markerDepth == null) return;
    var texture = markerDepth.texture();
    markerDepth.close();
    texture.close();
    markerDepth = null;
  }

  private GpuTextureView markerDepth(PortalMarker.Frame frame, GpuTextureView color) {
    if (frame.portalDepth == null) {
      int width = color.getWidth(0), height = color.getHeight(0);
      var device = RenderSystem.getDevice();
      if (markerDepth == null
          || markerDepth.getWidth(0) != width || markerDepth.getHeight(0) != height) {
        releaseMarkerDepth();
        var texture = device.createTexture(
            "Portal marker occlusion",
            GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
            GpuFormat.R32_FLOAT, width, height, 1, 1);
        markerDepth = device.createTextureView(texture);
      }
      device.createCommandEncoder().clearColorTexture(markerDepth.texture(), new Vector4f());
      frame.portalDepth = markerDepth;
    }
    return frame.portalDepth;
  }

  private record PreparedDraw(
      PreparedRenderType renderType,
      StagedVertexBuffer.Draw geometry,
      PortalParticleMesh.Batch particles) {}

  private record PreparedGroup(
      List<PreparedDraw> draws, Background background, PortalScreenRegion backgroundRegion,
      PortalMarker.Frame markers, boolean solid) {}

  private final class ParticleGeometry implements AutoCloseable {
    GpuBuffer vertices;
    PortalParticleMesh.Batch uploaded;
    int indexCount;
    boolean used;

    void update(PortalParticleMesh.Batch batch, ByteBufferBuilder storage) {
      used = true;
      if (uploaded != batch) {
        var builder =
            new BufferBuilder(
                storage, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buildParticles(batch, builder);
        try (var mesh = builder.buildOrThrow()) {
          int size = mesh.vertexBuffer().remaining();
          if (vertices == null || vertices.size() < size) {
            if (vertices != null) particleBuffers.add(vertices);
            vertices = acquireParticleBuffer(size);
          }
          var encoder = RenderSystem.getDevice().createCommandEncoder();
          var staging =
              encoder
                  .transientMemory()
                  .uploadStaging(mesh.vertexBuffer(), 4, GpuBuffer.USAGE_COPY_SRC);
          // Device copies are ordered after earlier draws, including uses by a retired effect.
          encoder.copyToBuffer(staging, vertices.slice(0, size));
          indexCount = mesh.drawState().indexCount();
        }
        uploaded = batch;
      }
    }

    StagedVertexBuffer.ExecuteInfo info() {
      var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
      return new StagedVertexBuffer.ExecuteInfo(
          vertices, indices.getBuffer(indexCount), indices.type(), 0, 0, indexCount, PrimitiveTopology.QUADS);
    }

    @Override
    public void close() {
      if (vertices != null) {
        particleBuffers.add(vertices);
        vertices = null;
      }
    }
  }

  private GpuBuffer acquireParticleBuffer(int size) {
    int best = -1;
    for (int i = 0; i < particleBuffers.size(); i++) {
      long capacity = particleBuffers.get(i).size();
      if (capacity >= size && (best < 0 || capacity < particleBuffers.get(best).size())) best = i;
    }
    if (best >= 0) return particleBuffers.remove(best);
    return RenderSystem.getDevice()
        .createBuffer(
            () -> "Portal particle geometry",
            GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
            Mth.smallestEncompassingPowerOfTwo(size));
  }

  private static final class Background implements AutoCloseable {
    final GpuTexture source;
    final GpuTexture texture;
    final GpuTextureView view;

    Background(GpuTexture source) {
      this.source = source;
      texture =
          RenderSystem.getDevice()
              .createTexture(
                  "Portal refraction",
                  GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                  source.getFormat(),
                  source.getWidth(0),
                  source.getHeight(0),
                  1,
                  1);
      view = RenderSystem.getDevice().createTextureView(texture);
    }

    @Override
    public void close() {
      view.close();
      texture.close();
    }
  }

  private final List<PreparedGroup> groups = new ArrayList<>();
  private static final ParticlePipelines REFRACTION =
      particlePipelines("portal_close_refraction", true, false);
  private static final ParticlePipelines TRANSLUCENT_PARTICLES =
      particlePipelines("portal_translucent_particles", false, false);
  private static final ParticlePipelines TINTED_PARTICLES =
      particlePipelines("portal_tinted_particles", false, true);

  private record ParticlePipelines(RenderPipeline classic, OitPipelineSet oit) {}

  private static ParticlePipelines particlePipelines(String name, boolean refraction, boolean tinted) {
    var builder = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
        .withVertexShader(PortalixMod.id("core/portal_particle"))
        .withFragmentShader(PortalixMod.id(
            refraction ? "core/portal_particle_refract" : "core/portal_particle"))
        .withCull(false);
    if (refraction) builder.withBindGroupLayout(BindGroupLayouts.SAMPLER1);
    if (tinted) builder.withShaderDefine("PORTALIX_TINTED_PARTICLE");
    var oit = OitPipelineSet.builder("portalix_" + name, builder).build();
    RenderPipelines.register(oit.depthBoundsPipeline());
    RenderPipelines.register(oit.transmittancePipeline());
    RenderPipelines.register(oit.accumulatePipeline());
    var classic = RenderPipelines.register(builder
        .withLocation(PortalixMod.id("pipeline/" + name))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
        .build());
    return new ParticlePipelines(classic, oit);
  }


  public sealed interface Node extends SubmitNode permits Submit, PortalMarker {
    @Override
    default FeatureRendererType<Node> featureType() {
      return TYPE;
    }
  }

  public record Submit(
      PortalEndpoint endpoint,
      boolean orange,
      PortalixVariant variant,
      float open,
      float staticAmount,
      float time,
      boolean surface,
      boolean active,
      Vec3 camera,
      List<PortalParticleMesh.Batch> particles,
      PortalMarker.Frame markers)
      implements Node {}

  @Override
  public void prepareGroup(FeatureFrameContext context, List<Node> submits, boolean outline) {
    if (IrisShaders.shadowPass()) {
      groups.add(new PreparedGroup(List.of(), null, null, null, false));
      return;
    }
    Background background = null;
    PortalScreenRegion backgroundRegion = null;
    boolean bundledShaders = ModResourceReload.bundledPortalShaders();
    boolean oit = Minecraft.getInstance().gameRenderer.useImprovedTransparency();
    Matrix4f viewProjection = null;
    StagedVertexBuffer staged = context.stagedVertexBuffer();
    List<PreparedDraw> draws = new ArrayList<>();
    PortalMarker.Frame markers = null;
    boolean solid = false;
    for (var node : submits) {
      if (node instanceof PortalMarker marker) {
        markers = marker.frame();
        draws.add(new PreparedDraw(marker.prepare(context), marker.appendGeometry(staged), null));
        continue;
      }
      var submit = (Submit) node;
      var endpoint = submit.endpoint;
      Vec3 right = endpoint.right();
      Vec3 up = endpoint.up();
      Vec3 normal = endpoint.normal();
      Matrix4f basis =
          new Matrix4f()
              .setColumn(0, new Vector4f((float) right.x, (float) right.y, (float) right.z, 0))
              .setColumn(1, new Vector4f((float) up.x, (float) up.y, (float) up.z, 0))
              .setColumn(2, new Vector4f((float) normal.x, (float) normal.y, (float) normal.z, 0));
      List<PortalSurfaceMesh.Vertex> mesh = List.of();
      if (submit.surface && submit.open > 0) {
        if (viewProjection == null)
          viewProjection =
              PortalSceneManager.surfaceProjection().mul(RenderSystem.getModelViewMatrixCopy());
        mesh = PortalSurfaceMesh.mesh(endpoint, submit.camera, viewProjection);
      }
      if (mesh.size() >= 3) {
        solid = true;
        if (bundledShaders) markers = submit.markers;
        if (background == null) background = prepareBackground();
        int width = background.texture.getWidth(0), height = background.texture.getHeight(0);
        var region = bundledShaders
            ? PortalScreenRegion.project(mesh, viewProjection, width, height)
            : PortalScreenRegion.full(width, height);
        var scene = PortalSceneManager.portalImage(endpoint);
        if (!bundledShaders) {
          backgroundRegion = region;
        } else if (scene == null || !PortalSurfaceMesh.insideAperture(mesh, submit.open)) {
          // The opening shader refracts outside the mesh. A settled rim only reads under itself.
          var samples = submit.open < 1 ? PortalScreenRegion.full(width, height) : region;
          backgroundRegion = samples.union(backgroundRegion);
        }
        double clearance = submit.camera.subtract(endpoint.center()).dot(normal);
        // Bias along each camera ray so the surface stays in front of its wall at grazing angles.
        float depthScale = (float) Math.max(.5, 1 - PortalSurfaceMesh.NORMAL_OFFSET / clearance);
        var noise =
            context
                .textureManager()
                .getTexture(
                    PortalixMod.id("textures/portals/models/portals/noise-blur-256x256.png"));
        var color =
            context
                .textureManager()
                .getTexture(
                    PortalixMod.id(
                        "textures/portals/models/portals/portal-"
                            + (submit.orange ? "orange" : "blue")
                            + "-color.png"));
        boolean tintedRim = submit.variant.usesTintedRim();
        int rgb = tintedRim ? submit.variant.rimColor(submit.orange) : 0;
        var depthTextures =
            IrisShaders.active() && !PortalSceneManager.isPortalView()
                ? ShaderBackend.INSTANCE.portalDepthTextures(scene != null)
                : null;
        var textures =
            new ArrayList<>(
                List.of(
                    new PreparedRenderType.Texture(
                        "Sampler0",
                        background.view,
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),
                    new PreparedRenderType.Texture(
                        "Sampler1",
                        noise.getTextureView(),
                        RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR)),
                    new PreparedRenderType.Texture(
                        "Sampler2", color.getTextureView(), color.getSampler()),
                    new PreparedRenderType.Texture(
                        "PortalScene",
                        scene == null ? background.view : scene.texture(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))));
        if (depthTextures != null)
          Collections.addAll(textures, depthTextures.world(), depthTextures.hand(), depthTextures.occlusion());
        var surface =
            new PreparedRenderType(
                "Portal surface", depthTextures == null ? PIPELINE : IRIS_HAND_PIPELINE, null,
                RenderSystem.getDynamicUniforms()
                    .writeTransform(
                        RenderSystem.getModelViewMatrixCopy(),
                        new Vector4f(
                            (rgb >> 16 & 255) / 255.0F,
                            (rgb >> 8 & 255) / 255.0F,
                            (rgb & 255) / 255.0F,
                            depthScale),
                        new Vector3f(submit.open, submit.staticAmount, submit.time),
                        new Matrix4f(basis)
                            .scale(
                                PortalSurfaceMesh.SOURCE_X,
                                PortalSurfaceMesh.SOURCE_Y,
                                PortalSurfaceMesh.SOURCE_X)
                            .setColumn(
                                2, scene == null ? new Vector4f(1, 1, 0, 0) : scene.uvTransform())
                            .m30(scene == null ? 0 : 1)
                            .m31(tintedRim ? 1 : 0)
                            .m32(RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 1 : 0)
                            .m33(PortalSurfaceMesh.UV_SCALE)),
                new com.mojang.blaze3d.systems.ScissorState(
                    RenderSystem.getScissorStateForRenderTypeDraws()),
                textures);
        var geometry =
            staged.appendDraw(DefaultVertexFormat.POSITION_TEX_COLOR, PrimitiveTopology.TRIANGLES);
        buildSurface(mesh, staged.getVertexBuilder(geometry));
        draws.add(new PreparedDraw(surface, geometry, null));
        if (depthTextures != null && scene != null)
          ShaderBackend.INSTANCE.aperture(
              region, () -> drawAperture(surface, staged.getExecuteInfo(geometry)));
      }
      if (submit.particles.isEmpty()) continue;
      Vec3 plane = endpoint.center().subtract(submit.camera);
      Vec3 center = plane.add(normal.scale(PortalSurfaceMesh.NORMAL_OFFSET));
      var transform =
          RenderSystem.getDynamicUniforms()
              .writeTransform(
                  RenderSystem.getModelViewMatrixCopy(),
                  new Vector4f(1),
                  new Vector3f((float) center.x, (float) center.y, (float) center.z),
                  new Matrix4f(basis)
                      .m03(RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 1 : 0)
                      .m13(PortalSurfaceMesh.UV_SCALE)
                      .m23(submit.active ? 1 : 0)
                      .setColumn(
                          3,
                          new Vector4f(
                              (float) plane.x,
                              (float) plane.y,
                              (float) plane.z,
                              PortalOpeningAnimation.apertureScale(submit.open))));
      for (var batch : submit.particles) {
        var texture =
            context
                .textureManager()
                .getTexture(PortalixMod.id("textures/portals/" + batch.texture() + ".png"));
        List<PreparedRenderType.Texture> bindings = new ArrayList<>();
        bindings.add(
            new PreparedRenderType.Texture(
                "Sampler0", texture.getTextureView(), texture.getSampler()));
        if (batch.refract()) {
          if (background == null) background = prepareBackground();
          backgroundRegion =
              PortalScreenRegion.full(background.texture.getWidth(0), background.texture.getHeight(0));
          bindings.add(
              new PreparedRenderType.Texture(
                  "Sampler1",
                  background.view,
                  RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)));
        }
        var pipelines = batch.refract() ? REFRACTION
            : batch.additive() ? TINTED_PARTICLES : TRANSLUCENT_PARTICLES;
        var particles =
            new PreparedRenderType(
                "Portal particles", pipelines.classic, pipelines.oit,
                transform,
                new com.mojang.blaze3d.systems.ScissorState(
                    RenderSystem.getScissorStateForRenderTypeDraws()),
                bindings);
        if (batch.persistentKey() != null) {
          // Keep the shared index format stable after the staged draws have captured it.
          RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)
              .requestIndexCount(batch.vertices().size() / 4 * 6);
          if (oit) updateParticleGeometry(batch);
          draws.add(new PreparedDraw(particles, null, batch));
        } else {
          var geometry =
              staged.appendDraw(DefaultVertexFormat.POSITION_TEX_COLOR, PrimitiveTopology.QUADS);
          buildParticles(batch, staged.getVertexBuilder(geometry));
          draws.add(new PreparedDraw(particles, geometry, null));
        }
      }
    }
    groups.add(new PreparedGroup(draws, background, backgroundRegion, markers, solid));
  }

  @Override
  public void finishPrepare(FeatureFrameContext context) {
    if (groups.isEmpty() || !Minecraft.getInstance().gameRenderer.useImprovedTransparency()) return;
    RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).resizeToRequestedIndexCount();
    if (groups.stream().anyMatch(group -> !group.solid
        && (group.markers != null || group.backgroundRegion != null))) {
      PortalLevelDraws.beforeOit(() -> setupOit(context.stagedVertexBuffer()));
    }
  }

  private void setupOit(StagedVertexBuffer staged) {
    var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
    var color = target.getColorTextureView();
    var depth = target.getDepthTextureView();
    Background background = null;
    PortalScreenRegion region = null;
    for (var group : groups) {
      if (group.solid) continue;
      var markers = group.markers;
      if (markers != null) {
        for (var draw : group.draws) {
          var type = draw.renderType;
          if (!PortalMarker.isMarker(type.pipeline())) continue;
          var geometry = staged.getExecuteInfo(draw.geometry);
          if (geometry == null) continue;
          if (markers.portalDepth != null) type = PortalMarker.withOcclusion(type, markers.portalDepth);
          drawToTarget(type, geometry, color, depth);
        }
      }
      if (group.backgroundRegion != null) {
        background = group.background;
        region = group.backgroundRegion.union(region);
      }
    }
    if (region != null) copyBackground(background, color.texture(), region);
  }

  private ParticleGeometry updateParticleGeometry(PortalParticleMesh.Batch batch) {
    var geometry = particleGeometry.computeIfAbsent(batch.persistentKey(), ignored -> new ParticleGeometry());
    geometry.update(batch, particleStorage);
    return geometry;
  }

  private Background prepareBackground() {
    var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
    var source = target.getColorTexture();
    var background = backgrounds.get(target);
    if (background == null || background.source != source) {
      if (background != null) background.close();
      background = new Background(source);
      backgrounds.put(target, background);
    }
    return background;
  }

  @Override
  public void executeGroup(
      FeatureFrameContext context, OitStage stage, RenderPass pass, int groupIndex,
      List<Node> submits, boolean strictlyOrdered) {
    if (IrisShaders.shadowPass()) return;
    var group = groups.get(groupIndex);
    if (group.draws.isEmpty()) return;
    if (stage != null) {
      for (var draw : group.draws) {
        if (draw.renderType.oitPipelineSet() == null) continue;
        var geometry = draw.particles == null
            ? context.stagedVertexBuffer().getExecuteInfo(draw.geometry)
            : particleGeometry.get(draw.particles.persistentKey()).info();
        if (geometry != null) draw.renderType.drawFromBufferOit(geometry, stage, pass);
      }
      return;
    }
    var draws = new ArrayList<SurfaceDraw>(group.draws.size());
    for (var draw : group.draws) {
      var geometry =
          draw.particles == null
              ? context.stagedVertexBuffer().getExecuteInfo(draw.geometry)
              : null;
      draws.add(new SurfaceDraw(draw.renderType, geometry, draw.particles));
    }
    Runnable render = () -> executeSurfaces(group, draws);
    if (IrisShaders.active()) IrisEffectTarget.deferDraw(render);
    else if (group.solid) PortalLevelDraws.solid(render);
    else PortalLevelDraws.afterTerrain(render);
  }

  private record SurfaceDraw(
      PreparedRenderType renderType,
      StagedVertexBuffer.ExecuteInfo geometry,
      PortalParticleMesh.Batch particles) {}

  private static void drawAperture(
      PreparedRenderType surface, StagedVertexBuffer.ExecuteInfo geometry) {
    if (geometry == null) return;
    var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
    drawToTarget(
        new PreparedRenderType("Portal aperture", APERTURE_PIPELINE, null,
            surface.dynamicTransforms(), surface.scissorState(), surface.textures().stream()
                .filter(texture -> texture.name().equals("PortalOcclusionDepth")).toList()),
        geometry, IrisEffectTarget.color(target), IrisEffectTarget.depth(target));
  }

  @SuppressWarnings("try")
  private void executeSurfaces(PreparedGroup group, List<SurfaceDraw> draws) {
    try (var ignored = IrisEffectTarget.begin()) {
      drawSurfaces(group, draws);
    }
  }

  private void drawSurfaces(PreparedGroup group, List<SurfaceDraw> draws) {
    var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
    var color = IrisEffectTarget.color(target);
    var depth = IrisEffectTarget.depth(target);
    if (group.backgroundRegion != null)
      copyBackground(group.background, color.texture(), group.backgroundRegion);
    for (SurfaceDraw draw : draws) {
      if (draw.particles != null) {
        var geometry = updateParticleGeometry(draw.particles).info();
        drawToTarget(draw.renderType, geometry, color, depth);
      } else {
        if (draw.geometry == null) continue;
        var pipeline = draw.renderType.pipeline();
        var markers = group.markers;
        if (markers != null && markers.portalDepth != null && PortalMarker.isMarker(pipeline)) {
          drawToTarget(PortalMarker.withOcclusion(draw.renderType, markers.portalDepth), draw.geometry, color, depth);
        } else if (markers != null && markers.submitted
            && (pipeline == PIPELINE || pipeline == IRIS_HAND_PIPELINE)) {
          drawSurfaceWithMarkerDepth(draw, markers, color, depth);
        } else {
          drawToTarget(draw.renderType, draw.geometry, color, depth);
        }
      }
    }
  }

  private static void copyBackground(Background background, GpuTexture source, PortalScreenRegion region) {
    if (source.getWidth(0) != background.texture.getWidth(0)
        || source.getHeight(0) != background.texture.getHeight(0))
      region = PortalScreenRegion.full(source.getWidth(0), source.getHeight(0));
    RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
        source, background.texture, 0, region.x(), region.y(),
        region.x(), region.y(), region.width(), region.height());
  }

  private static void drawToTarget(
      PreparedRenderType type, StagedVertexBuffer.ExecuteInfo geometry,
      GpuTextureView color, GpuTextureView depth) {
    var pipeline = IrisEffectTarget.pipeline(type.pipeline(), color.texture().getFormat());
    if (pipeline != type.pipeline())
      type = new PreparedRenderType(
          type.name(), pipeline, null, type.dynamicTransforms(), type.scissorState(), type.textures());
    try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
        type::name, color, Optional.empty(), depth, OptionalDouble.empty())) {
      type.drawFromBuffer(geometry, pass);
    }
  }

  private void drawSurfaceWithMarkerDepth(
      SurfaceDraw draw, PortalMarker.Frame markers, GpuTextureView color, GpuTextureView depth) {
    var surface = draw.renderType;
    // Record only the fragments that pass the same tests as the visible portal surface.
    var descriptor = RenderPassDescriptor.builder(() -> "Portal surface with marker occlusion")
        .withColorAttachment(color)
        .withColorAttachment(markerDepth(markers, color))
        .withDepthAttachment(depth)
        .withRenderArea(new RenderArea(0, 0, color.getWidth(0), color.getHeight(0)))
        .build();
    var pipeline =
        surface.pipeline() == PIPELINE ? MARKER_OCCLUSION_PIPELINE : IRIS_MARKER_OCCLUSION_PIPELINE;
    var geometry = draw.geometry;
    try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor)) {
      pass.setPipeline(RenderSystem.getCompiledPipeline(
          IrisEffectTarget.pipeline(pipeline, color.texture().getFormat())));
      var scissor = surface.scissorState();
      if (scissor.enabled())
        pass.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
      RenderSystem.bindDefaultUniforms(pass);
      pass.setUniform("DynamicTransforms", surface.dynamicTransforms());
      for (var texture : surface.textures())
        pass.setUniform(texture.name(), texture.textureView(), texture.sampler());
      pass.setVertexBuffer(0, geometry.vertexBuffer().slice());
      pass.setIndexBuffer(geometry.indexBuffer(), geometry.indexType());
      pass.drawIndexed(geometry.indexCount(), 1, geometry.firstIndex(), geometry.baseVertex(), 0);
    }
  }

  private static void buildSurface(List<PortalSurfaceMesh.Vertex> polygon, VertexConsumer builder) {
    for (int i = 1; i + 1 < polygon.size(); i++) {
      for (var vertex : List.of(polygon.getFirst(), polygon.get(i), polygon.get(i + 1))) {
        builder
            .addVertex((float) vertex.point().x, (float) vertex.point().y, (float) vertex.point().z)
            .setColor(-1)
            .setUv(vertex.u(), vertex.v())
            .setOverlay(0)
            .setLight(0)
            .setNormal(0, 0, 1);
      }
    }
  }

  private static void buildParticles(PortalParticleMesh.Batch batch, VertexConsumer builder) {
    for (var v : batch.vertices())
      builder
          .addVertex(v.x(), v.y(), v.z())
          .setColor(v.color())
          .setUv(v.u(), v.v())
          .setOverlay(0)
          .setLight(0)
          .setNormal(0, 0, 1);
  }

  @Override
  public void finishExecute(FeatureFrameContext context) {
    groups.clear();
  }

  @Override
  public void close() {
    releaseBackgrounds();
    releaseMarkerDepth();
    particleGeometry.values().forEach(ParticleGeometry::close);
    particleGeometry.clear();
    particleBuffers.forEach(GpuBuffer::close);
    particleBuffers.clear();
    particleStorage.close();
    groups.clear();
    INSTANCES.remove(this);
  }
}
