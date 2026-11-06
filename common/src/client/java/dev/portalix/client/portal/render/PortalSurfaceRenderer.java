package dev.portalix.client.portal.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.portalix.PortalixMod;
import dev.portalix.api.PortalEndpoint;
import dev.portalix.client.compat.iris.IrisEffectTarget;
import dev.portalix.client.compat.ShaderBackend;
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
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class PortalSurfaceRenderer implements FeatureRenderer<PortalSurfaceRenderer.Submit> {
  public static final FeatureRendererType<Submit> TYPE =
      FeatureRendererType.create("portalix_portal");
  private static final RenderPipeline PIPELINE =
      RenderPipelines.register(surfacePipeline("portal").build());
  private static final RenderPipeline IRIS_HAND_PIPELINE =
      RenderPipelines.register(handSurfacePipeline("portal_iris_hand").build());
  private static final RenderPipeline APERTURE_PIPELINE =
      RenderPipelines.register(
          surfacePipeline("portal_aperture")
              .withShaderDefine("PORTALIX_IRIS_HAND")
              .withShaderDefine("PORTALIX_DEPTH_APERTURE")
              .withBindGroupLayout(
                  BindGroupLayout.builder().withSampler("PortalOcclusionDepth").build())
              .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, true))
              .withColorTargetState(
                  new ColorTargetState(
                      Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_NONE))
              .build());

  private static RenderPipeline.Builder surfacePipeline(String name) {
    return RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
        .withLocation(PortalixMod.id("pipeline/" + name))
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
        .withVertexShader(PortalixMod.id("core/portal"))
        .withFragmentShader(PortalixMod.id("core/portal"))
        .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER2)
        .withBindGroupLayout(BindGroupLayout.builder().withSampler("PortalScene").build())
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
        .withCull(false)
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
  }

  private static RenderPipeline.Builder handSurfacePipeline(String name) {
    return surfacePipeline(name)
        .withShaderDefine("PORTALIX_IRIS_HAND")
        .withBindGroupLayout(
            BindGroupLayout.builder()
                .withSampler("PortalWorldDepth")
                .withSampler("PortalHandDepth")
                .withSampler("PortalOcclusionDepth")
                .build());
  }

  private final Map<RenderTarget, Background> backgrounds = new IdentityHashMap<>();
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

  private record PreparedDraw(
      PreparedRenderType renderType,
      StagedVertexBuffer.Draw geometry,
      PortalParticleMesh.Batch particles) {}

  private record PreparedGroup(List<PreparedDraw> draws, Background background) {}

  private final class ParticleGeometry implements AutoCloseable {
    GpuBuffer vertices;
    PortalParticleMesh.Batch uploaded;
    int indexCount;
    boolean used;

    void draw(PortalParticleMesh.Batch batch, ByteBufferBuilder storage, PreparedRenderType type) {
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
      var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
      type.drawFromBuffer(
          vertices, indices.getBuffer(indexCount), indices.type(), 0, 0, indexCount);
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
  private static final RenderPipeline REFRACTION =
      RenderPipelines.register(
          RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
              .withLocation(PortalixMod.id("pipeline/portal_close_refraction"))
              .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
              .withVertexShader(PortalixMod.id("core/portal_particle"))
              .withFragmentShader(PortalixMod.id("core/portal_particle_refract"))
              .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
              .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
              .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
              .withCull(false)
              .build());
  private static final RenderPipeline TRANSLUCENT_PARTICLES =
      RenderPipelines.register(
          RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
              .withLocation(PortalixMod.id("pipeline/portal_translucent_particles"))
              .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
              .withVertexShader(PortalixMod.id("core/portal_particle"))
              .withFragmentShader(PortalixMod.id("core/portal_particle"))
              .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
              .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
              .withCull(false)
              .build());
  private static final RenderPipeline TINTED_PARTICLES =
      RenderPipelines.register(
          RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
              .withLocation(PortalixMod.id("pipeline/portal_tinted_particles"))
              .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
              .withVertexShader(PortalixMod.id("core/portal_particle"))
              .withFragmentShader(PortalixMod.id("core/portal_particle"))
              .withShaderDefine("PORTALIX_TINTED_PARTICLE")
              .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
              .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
              .withCull(false)
              .build());


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
      List<PortalParticleMesh.Batch> particles)
      implements SubmitNode {
    @Override
    public FeatureRendererType<Submit> featureType() {
      return TYPE;
    }
  }

  @Override
  public void prepareGroup(FeatureFrameContext context, List<Submit> submits, boolean outline) {
    if (IrisShaders.shadowPass()) {
      groups.add(new PreparedGroup(List.of(), null));
      return;
    }
    boolean needsBackground =
        submits.stream()
            .anyMatch(
                s -> s.surface || s.particles.stream().anyMatch(PortalParticleMesh.Batch::refract));
    Background background = needsBackground ? prepareBackground() : null;
    StagedVertexBuffer staged = context.stagedVertexBuffer();
    List<PreparedDraw> draws = new ArrayList<>();
    for (Submit submit : submits) {
      var endpoint = submit.endpoint;
      Vec3 right = endpoint.right();
      Vec3 up = endpoint.up();
      Vec3 normal = endpoint.normal();
      Matrix4f basis =
          new Matrix4f()
              .setColumn(0, new Vector4f((float) right.x, (float) right.y, (float) right.z, 0))
              .setColumn(1, new Vector4f((float) up.x, (float) up.y, (float) up.z, 0))
              .setColumn(2, new Vector4f((float) normal.x, (float) normal.y, (float) normal.z, 0));
      if (submit.surface) {
        double clearance = submit.camera.subtract(endpoint.center()).dot(normal);
        // Bias along each camera ray so the surface stays in front of its wall at grazing angles.
        float depthScale = (float) Math.max(.5, 1 - PortalSurfaceMesh.NORMAL_OFFSET / clearance);
        var scene = PortalSceneManager.portalImage(endpoint);
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
        int rgb = submit.variant.particleColor(submit.orange);
        var handTextures =
            IrisShaders.active() && !PortalSceneManager.isPortalView()
                ? ShaderBackend.INSTANCE.handTextures()
                : List.<PreparedRenderType.Texture>of();
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
        textures.addAll(handTextures);
        var surface =
            new PreparedRenderType(
                handTextures.isEmpty() ? PIPELINE : IRIS_HAND_PIPELINE,
                OutputTarget.MAIN_TARGET,
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
                            .m31(submit.variant.cooperative() ? 1 : 0)
                            .m32(RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 1 : 0)
                            .m33(PortalSurfaceMesh.UV_SCALE)),
                new com.mojang.blaze3d.systems.ScissorState(
                    RenderSystem.getScissorStateForRenderTypeDraws()),
                textures);
        var geometry =
            staged.appendDraw(DefaultVertexFormat.POSITION_TEX_COLOR, PrimitiveTopology.TRIANGLES);
        buildSurface(submit, staged.getVertexBuilder(geometry));
        draws.add(new PreparedDraw(surface, geometry, null));
        if (!handTextures.isEmpty() && scene != null)
          ShaderBackend.INSTANCE.aperture(
              () -> drawAperture(surface, staged.getExecuteInfo(geometry)));
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
        if (batch.refract())
          bindings.add(
              new PreparedRenderType.Texture(
                  "Sampler1",
                  background.view,
                  RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)));
        RenderPipeline particlePipeline;
        if (batch.refract()) particlePipeline = REFRACTION;
        else particlePipeline = batch.additive() ? TINTED_PARTICLES : TRANSLUCENT_PARTICLES;
        var particles =
            new PreparedRenderType(
                particlePipeline,
                OutputTarget.MAIN_TARGET,
                transform,
                new com.mojang.blaze3d.systems.ScissorState(
                    RenderSystem.getScissorStateForRenderTypeDraws()),
                bindings);
        if (batch.persistentKey() != null) {
          draws.add(new PreparedDraw(particles, null, batch));
        } else {
          var geometry =
              staged.appendDraw(DefaultVertexFormat.POSITION_TEX_COLOR, PrimitiveTopology.QUADS);
          buildParticles(batch, staged.getVertexBuilder(geometry));
          draws.add(new PreparedDraw(particles, geometry, null));
        }
      }
    }
    groups.add(new PreparedGroup(draws, background));
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
      FeatureFrameContext context, int groupIndex, List<Submit> submits, boolean outline) {
    if (IrisShaders.shadowPass()) return;
    var group = groups.get(groupIndex);
    var draws = new ArrayList<SurfaceDraw>(group.draws.size());
    for (var draw : group.draws) {
      var geometry =
          draw.particles == null
              ? context.stagedVertexBuffer().getExecuteInfo(draw.geometry)
              : null;
      draws.add(new SurfaceDraw(draw.renderType, geometry, draw.particles));
    }
    if (IrisShaders.active()) {
      ShaderBackend.INSTANCE.submit(() -> executeSurfaces(group.background, draws));
      return;
    }
    executeSurfaces(group.background, draws);
  }

  private record SurfaceDraw(
      PreparedRenderType renderType,
      StagedVertexBuffer.ExecuteInfo geometry,
      PortalParticleMesh.Batch particles) {}

  @SuppressWarnings("try")
  private static void drawAperture(
      PreparedRenderType surface, StagedVertexBuffer.ExecuteInfo geometry) {
    if (geometry == null) return;
    try (var ignored = IrisEffectTarget.begin()) {
      new PreparedRenderType(
              APERTURE_PIPELINE,
              surface.outputTarget(),
              surface.dynamicTransforms(),
              surface.scissorState(),
              surface.textures().stream()
                  .filter(
                      texture ->
                          !texture.name().equals("PortalWorldDepth")
                              && !texture.name().equals("PortalHandDepth"))
                  .toList())
          .drawFromBuffer(geometry);
    }
  }

  @SuppressWarnings("try")
  private void executeSurfaces(Background background, List<SurfaceDraw> draws) {
    try (var ignored = IrisEffectTarget.begin()) {
      drawSurfaces(background, draws);
    }
  }

  private void drawSurfaces(Background background, List<SurfaceDraw> draws) {
    if (background != null) {
      var color = RenderSystem.outputColorTextureOverride;
      var source = color == null ? background.source : color.texture();
      RenderSystem.getDevice()
          .createCommandEncoder()
          .copyTextureToTexture(
              source, background.texture, 0, 0, 0, 0, 0, source.getWidth(0), source.getHeight(0));
    }
    for (SurfaceDraw draw : draws) {
      if (draw.particles != null) {
        particleGeometry
            .computeIfAbsent(draw.particles.persistentKey(), ignored -> new ParticleGeometry())
            .draw(draw.particles, particleStorage, draw.renderType);
      } else {
        if (draw.geometry != null) draw.renderType.drawFromBuffer(draw.geometry);
      }
    }
  }

  private static void buildSurface(Submit submit, VertexConsumer builder) {
    PortalEndpoint endpoint = submit.endpoint;
    var polygon =
        PortalSurfaceMesh.mesh(
            endpoint,
            submit.camera,
            PortalSceneManager.surfaceProjection().mul(RenderSystem.getModelViewMatrixCopy()));
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
    particleGeometry.values().forEach(ParticleGeometry::close);
    particleGeometry.clear();
    particleBuffers.forEach(GpuBuffer::close);
    particleBuffers.clear();
    particleStorage.close();
    groups.clear();
    INSTANCES.remove(this);
  }
}
