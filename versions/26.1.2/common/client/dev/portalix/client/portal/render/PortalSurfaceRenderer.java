package dev.portalix.client.portal.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.portalix.PortalixMod;
import dev.portalix.api.PortalEndpoint;
import dev.portalix.client.ModResourceReload;
import dev.portalix.client.compat.iris.IrisEffectTarget;
import dev.portalix.client.compat.ShaderBackend;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.particle.PortalParticleMesh;
import dev.portalix.client.platform.ClientPlatform.Phase;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.client.render.LinearParticleRenderer;
import dev.portalix.client.render.PortalDraw;
import dev.portalix.client.render.PortalRenderQueue;
import dev.portalix.client.render.PortalTextures;
import dev.portalix.item.PortalixVariant;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL30;

public final class PortalSurfaceRenderer {
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
                  RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET))
              .withShaderDefine("PORTALIX_IRIS_HAND")
              .withShaderDefine("PORTALIX_DEPTH_APERTURE")
              .withSampler("PortalOcclusionDepth")
              .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, true))
              .withColorTargetState(
                  new ColorTargetState(
                      Optional.empty(), ColorTargetState.WRITE_NONE))
              .build());

  private static RenderPipeline.Builder portalPipeline(String name, RenderPipeline.Builder builder) {
    return builder
        .withLocation(PortalixMod.id("pipeline/" + name))
        .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.TRIANGLES)
        .withVertexShader(PortalixMod.id("core/portal"))
        .withFragmentShader(PortalixMod.id("core/portal"))
        .withCull(false);
  }

  private static RenderPipeline.Builder surfacePipeline(String name) {
    return portalPipeline(name, RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET))
        .withSampler("Sampler1")
        .withSampler("Sampler2")
        .withSampler("PortalScene")
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false));
  }

  private static RenderPipeline.Builder handSurfacePipeline(String name) {
    return surfacePipeline(name)
        .withShaderDefine("PORTALIX_IRIS_HAND")
        .withSampler("PortalWorldDepth")
        .withSampler("PortalHandDepth")
        .withSampler("PortalOcclusionDepth");
  }

  private static RenderPipeline markerOcclusionPipeline(RenderPipeline.Builder builder) {
    return builder
        .withShaderDefine("PORTALIX_MARKER_OCCLUSION")
        .withColorTargetState(ColorTargetState.DEFAULT)
        .build();
  }

  private static final PortalSurfaceRenderer INSTANCE = new PortalSurfaceRenderer();
  private static final PortalRenderQueue.Feature<Node> FEATURE = new PortalRenderQueue.Feature<>(INSTANCE::draw);

  private final Map<GpuTexture, Background> backgrounds = new IdentityHashMap<>();
  private GpuTextureView markerDepth;
  private final Map<Object, ParticleGeometry> particleGeometry = new IdentityHashMap<>();
  private final List<GpuBuffer> particleBuffers = new ArrayList<>();
  private final ByteBufferBuilder geometryStorage = new ByteBufferBuilder(4096);

  private PortalSurfaceRenderer() {}

  public static void clearResources() {
    if (ShaderBackend.INSTANCE != null) ShaderBackend.INSTANCE.clearComposite();
    IrisEffectTarget.clear();
    LinearParticleRenderer.clearResources();
    INSTANCE.releaseBackgrounds();
    INSTANCE.releaseMarkerDepth();
    INSTANCE.particleGeometry.values().forEach(ParticleGeometry::close);
    INSTANCE.particleGeometry.clear();
    INSTANCE.particleBuffers.forEach(GpuBuffer::close);
    INSTANCE.particleBuffers.clear();
  }

  public static void endFrame() {
    IrisEffectTarget.endFrame();
    LinearParticleRenderer.endFrame();
    INSTANCE.backgrounds.entrySet().removeIf(entry -> {
      if (!entry.getKey().isClosed()) return false;
      entry.getValue().close();
      return true;
    });
    INSTANCE.particleGeometry.values().removeIf(geometry -> {
      if (geometry.used) {
        geometry.used = false;
        return false;
      }
      geometry.close();
      return true;
    });
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
        var texture = PortalTextures.color("Portal marker occlusion",
            GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
            width, height, GL30.GL_R32F);
        markerDepth = device.createTextureView(texture);
      }
      device.createCommandEncoder().clearColorTexture(markerDepth.texture(), -1);
      frame.portalDepth = markerDepth;
    }
    return frame.portalDepth;
  }

  private record PreparedDraw(PortalDraw pass, Consumer<VertexConsumer> geometry,
      List<String> backgroundSamplers, PortalParticleMesh.Batch particles) {}

  private final class ParticleGeometry implements AutoCloseable {
    GpuBuffer vertices;
    PortalParticleMesh.Batch uploaded;
    int indexCount;
    boolean used;

    void draw(PortalParticleMesh.Batch batch, ByteBufferBuilder storage, PortalDraw type) {
      used = true;
      if (uploaded != batch) {
        var builder =
            new BufferBuilder(
                storage, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buildParticles(batch, builder);
        try (var mesh = builder.buildOrThrow()) {
          int size = mesh.vertexBuffer().remaining();
          if (vertices == null || vertices.size() < size) {
            if (vertices != null) particleBuffers.add(vertices);
            vertices = acquireParticleBuffer(size);
          }
          RenderSystem.getDevice().createCommandEncoder()
              .writeToBuffer(vertices.slice(0, size), mesh.vertexBuffer());
          indexCount = mesh.drawState().indexCount();
        }
        uploaded = batch;
      }
      var indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
      type.draw(new PortalDraw.Geometry(vertices, indices.getBuffer(indexCount), indices.type(), indexCount));
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
    final GpuTexture texture;
    final GpuTextureView view;

    Background(GpuTextureView source) {
      texture = PortalTextures.allocateLike("Portal refraction",
          GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, source);
      view = RenderSystem.getDevice().createTextureView(texture);
    }

    @Override
    public void close() {
      view.close();
      texture.close();
    }
  }

  private static final RenderPipeline REFRACTION =
      RenderPipelines.register(
          RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
              .withLocation(PortalixMod.id("pipeline/portal_close_refraction"))
              .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
              .withVertexShader(PortalixMod.id("core/portal_particle"))
              .withFragmentShader(PortalixMod.id("core/portal_particle_refract"))
              .withSampler("Sampler1")
              .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
              .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
              .withCull(false)
              .build());
  private static final RenderPipeline TRANSLUCENT_PARTICLES =
      RenderPipelines.register(
          RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
              .withLocation(PortalixMod.id("pipeline/portal_translucent_particles"))
              .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
              .withVertexShader(PortalixMod.id("core/portal_particle"))
              .withFragmentShader(PortalixMod.id("core/portal_particle"))
              .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
              .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
              .withCull(false)
              .build());
  private static final RenderPipeline TINTED_PARTICLES =
      RenderPipelines.register(
          RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
              .withLocation(PortalixMod.id("pipeline/portal_tinted_particles"))
              .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
              .withVertexShader(PortalixMod.id("core/portal_particle"))
              .withFragmentShader(PortalixMod.id("core/portal_particle"))
              .withShaderDefine("PORTALIX_TINTED_PARTICLE")
              .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
              .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
              .withCull(false)
              .build());


  public sealed interface Node extends PortalRenderQueue.Submission permits Submit, PortalMarker {
    @Override
    default void enqueue(PortalRenderQueue queue, Phase phase) {
      queue.add(phase, FEATURE, this);
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

  private void draw(List<Node> submits) {
    if (IrisShaders.shadowPass()) return;

    var client = Minecraft.getInstance();
    var target = client.getMainRenderTarget();
    int width = target.width;
    int height = target.height;
    PortalScreenRegion backgroundRegion = null;
    boolean bundledShaders = ModResourceReload.bundledPortalShaders();
    Matrix4f viewProjection = null;
    List<PreparedDraw> draws = new ArrayList<>(submits.size());
    PortalMarker.Frame markers = null;
    for (var node : submits) {
      if (node instanceof PortalMarker marker) {
        markers = marker.frame();
        draws.add(new PreparedDraw(marker.prepare(), marker::buildGeometry, List.of(), null));
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
              PortalSceneManager.surfaceProjection().mul(RenderSystem.getModelViewMatrix());
        mesh = PortalSurfaceMesh.mesh(endpoint, submit.camera, viewProjection);
      }
      if (mesh.size() >= 3) {
        if (bundledShaders) markers = submit.markers;
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
            client
                .getTextureManager()
                .getTexture(
                    PortalixMod.id("textures/portals/models/portals/noise-blur-256x256.png"));
        var color =
            client
                .getTextureManager()
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
        var textures = new ArrayList<PortalDraw.Texture>();
        textures.add(new PortalDraw.Texture("Sampler1", noise.getTextureView(),
            RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR)));
        textures.add(new PortalDraw.Texture("Sampler2", color.getTextureView(), color.getSampler()));
        if (scene != null) {
          textures.add(new PortalDraw.Texture("PortalScene", scene.texture(),
              RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)));
        }
        if (depthTextures != null) {
          textures.add(new PortalDraw.Texture("PortalWorldDepth", depthTextures.world().textureView(), depthTextures.world().sampler()));
          textures.add(new PortalDraw.Texture("PortalHandDepth", depthTextures.hand().textureView(), depthTextures.hand().sampler()));
          textures.add(new PortalDraw.Texture("PortalOcclusionDepth", depthTextures.occlusion().textureView(), depthTextures.occlusion().sampler()));
        }
        var surface =
            new PortalDraw(
                depthTextures == null ? PIPELINE : IRIS_HAND_PIPELINE,
                OutputTarget.MAIN_TARGET,
                RenderSystem.getDynamicUniforms()
                    .writeTransform(
                        RenderSystem.getModelViewMatrix(),
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
                            .m32(RenderSystem.getDevice().isZZeroToOne() ? 1 : 0)
                            .m33(PortalSurfaceMesh.UV_SCALE)),
                PortalDraw.scissor(),
                textures);
        var polygon = mesh;
        Consumer<VertexConsumer> geometry = vertices -> buildSurface(polygon, vertices);
        var backgroundSamplers = scene == null ? List.of("Sampler0", "PortalScene") : List.of("Sampler0");
        draws.add(new PreparedDraw(surface, geometry, backgroundSamplers, null));
        if (depthTextures != null && scene != null)
          ShaderBackend.INSTANCE.aperture(region, () -> drawAperture(surface, geometry));
      }
      if (submit.particles.isEmpty()) continue;
      Vec3 plane = endpoint.center().subtract(submit.camera);
      Vec3 center = plane.add(normal.scale(PortalSurfaceMesh.NORMAL_OFFSET));
      var transform =
          RenderSystem.getDynamicUniforms()
              .writeTransform(
                  RenderSystem.getModelViewMatrix(),
                  new Vector4f(1),
                  new Vector3f((float) center.x, (float) center.y, (float) center.z),
                  new Matrix4f(basis)
                      .m03(RenderSystem.getDevice().isZZeroToOne() ? 1 : 0)
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
            client
                .getTextureManager()
                .getTexture(PortalixMod.id("textures/portals/" + batch.texture() + ".png"));
        List<PortalDraw.Texture> bindings = new ArrayList<>();
        bindings.add(
            new PortalDraw.Texture(
                "Sampler0", texture.getTextureView(), texture.getSampler()));
        if (batch.refract()) backgroundRegion = PortalScreenRegion.full(width, height);
        RenderPipeline particlePipeline;
        if (batch.refract()) particlePipeline = REFRACTION;
        else particlePipeline = batch.additive() ? TINTED_PARTICLES : TRANSLUCENT_PARTICLES;
        var particles =
            new PortalDraw(
                particlePipeline,
                OutputTarget.MAIN_TARGET,
                transform,
                PortalDraw.scissor(),
                bindings);
        draws.add(new PreparedDraw(particles, vertices -> buildParticles(batch, vertices),
            batch.refract() ? List.of("Sampler1") : List.of(), batch));
      }
    }
    if (draws.isEmpty()) return;

    var region = backgroundRegion;
    var frame = markers;
    if (IrisShaders.active()) IrisEffectTarget.deferDraw(() -> executeSurfaces(draws, region, frame, width, height));
    else executeSurfaces(draws, region, frame, width, height);
  }

  private void drawAperture(PortalDraw surface, Consumer<VertexConsumer> vertices) {
    var aperture = new PortalDraw(APERTURE_PIPELINE, surface.outputTarget(), surface.dynamicTransforms(),
        surface.scissorState(), surface.textures().stream()
            .filter(texture -> texture.name().equals("PortalOcclusionDepth")).toList());
    aperture.draw(upload(aperture.pipeline(), vertices));
  }

  private PortalDraw.Geometry upload(RenderPipeline pipeline, Consumer<VertexConsumer> vertices) {
    var builder = new BufferBuilder(geometryStorage, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
    vertices.accept(builder);
    try (var mesh = builder.buildOrThrow()) {
      return PortalDraw.upload(mesh);
    }
  }

  @SuppressWarnings("try")
  private void executeSurfaces(List<PreparedDraw> draws, PortalScreenRegion region,
      PortalMarker.Frame markers, int width, int height) {
    try (var scissor = PortalDraw.preserveScissor(); var ignored = IrisEffectTarget.begin()) {
      var target = OutputTarget.MAIN_TARGET.getRenderTarget();
      var color = RenderSystem.outputColorTextureOverride;
      if (color == null) color = target.getColorTextureView();
      var depth = RenderSystem.outputDepthTextureOverride;
      if (depth == null) depth = target.getDepthTextureView();

      Background background = null;
      if (draws.stream().anyMatch(draw -> !draw.backgroundSamplers.isEmpty())) {
        var source = color;
        background = backgrounds.computeIfAbsent(source.texture(), unused -> new Background(source));
        if (region != null) {
          if (source.getWidth(0) != width || source.getHeight(0) != height) {
            region = PortalScreenRegion.full(source.getWidth(0), source.getHeight(0));
          }
          RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
              source.texture(), background.texture, 0, region.x(), region.y(),
              region.x(), region.y(), region.width(), region.height());
        }
      }

      for (var draw : draws) {
        var pass = draw.pass;
        if (!draw.backgroundSamplers.isEmpty()) {
          var textures = new ArrayList<>(pass.textures());
          var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
          for (String name : draw.backgroundSamplers)
            textures.add(new PortalDraw.Texture(name, background.view, sampler));
          pass = new PortalDraw(pass.pipeline(), pass.outputTarget(), pass.dynamicTransforms(),
              pass.scissorState(), textures);
        }
        if (draw.particles != null && draw.particles.persistentKey() != null) {
          particleGeometry.computeIfAbsent(draw.particles.persistentKey(), unused -> new ParticleGeometry())
              .draw(draw.particles, geometryStorage, pass);
          continue;
        }

        var geometry = upload(pass.pipeline(), draw.geometry);
        if (markers != null && markers.portalDepth != null && PortalMarker.isMarker(pass.pipeline())) {
          PortalMarker.drawWithOcclusion(pass, geometry, markers.portalDepth);
        } else {
          pass.draw(geometry);
          if (markers != null && markers.submitted && (pass.pipeline() == PIPELINE || pass.pipeline() == IRIS_HAND_PIPELINE)) {
            var pipeline = pass.pipeline() == PIPELINE ? MARKER_OCCLUSION_PIPELINE : IRIS_MARKER_OCCLUSION_PIPELINE;
            var mask = new PortalDraw(pipeline, pass.outputTarget(), pass.dynamicTransforms(), pass.scissorState(), pass.textures());
            try (var renderPass = mask.begin(markerDepth(markers, color), depth)) {
              geometry.draw(renderPass);
            }
          }
        }
      }
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

}
