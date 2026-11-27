package dev.portalix.client.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.portalix.PortalixMod;
import dev.portalix.client.ModResourceReload;
import dev.portalix.client.compat.iris.IrisEffectTarget;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.platform.ClientPlatform.Phase;
import dev.portalix.client.platform.ClientPlatform;
import dev.portalix.client.portal.render.PortalScreenRegion;
import dev.portalix.client.portal.scene.PortalSceneManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL30;

public final class LinearParticleRenderer {
  private static final RenderPipeline COMPOSITE = RenderPipelines.register(
      screenPipeline("shot_light_composite").withSampler("Background").withSampler("Light").build());
  private static final RenderPipeline CLEAR = RenderPipelines.register(screenPipeline("shot_light_clear").build());
  private static final LinearParticleRenderer INSTANCE = new LinearParticleRenderer();
  private static final PortalRenderQueue.Feature<Submit> FEATURE = new PortalRenderQueue.Feature<>(INSTANCE::draw);

  private final ByteBufferBuilder storage = new ByteBufferBuilder(4096);
  private final List<ParticleGroup> deferred = new ArrayList<>();
  private GpuTexture light;
  private GpuTextureView lightView;
  private GpuTexture background;
  private GpuTextureView backgroundView;
  private GpuTexture originalColor;
  private int backgroundFormat;

  private LinearParticleRenderer() {}

  private static RenderPipeline.Builder screenPipeline(String name) {
    return RenderPipeline.builder()
        .withLocation(PortalixMod.id("pipeline/" + name))
        .withVertexShader("core/screenquad")
        .withFragmentShader(PortalixMod.id("core/" + name))
        .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
        .withColorTargetState(ColorTargetState.DEFAULT)
        .withCull(false);
  }

  public static void submit(
      OrderedSubmitNodeCollector collector,
      PoseStack pose,
      RenderType renderType,
      SubmitNodeCollector.CustomGeometryRenderer geometry) {
    ClientPlatform.INSTANCE.submit(collector,
        Phase.AFTER_TERRAIN,
        new Submit(pose.last().copy(), renderType, geometry));
  }

  public record Submit(
      PoseStack.Pose pose,
      RenderType renderType,
      SubmitNodeCollector.CustomGeometryRenderer geometry)
      implements PortalRenderQueue.Submission {
    @Override
    public void enqueue(PortalRenderQueue queue, Phase phase) {
      queue.add(phase, FEATURE, this);
    }
  }

  private void draw(List<Submit> submits) {
    if (IrisShaders.shadowPass()) return;
    var tracker = ModResourceReload.bundledParticleShaders() ? new BoundsTracker() : null;
    var scissor = PortalDraw.scissor();
    var modelView = RenderSystem.getModelViewMatrix();
    var transforms = RenderSystem.getDynamicUniforms()
        .writeTransform(modelView, new Vector4f(1), new Vector3f(), new Matrix4f());
    var batches = new LinkedHashMap<RenderType, List<Submit>>();
    for (var submit : submits)
      batches.computeIfAbsent(submit.renderType, unused -> new ArrayList<>()).add(submit);
    var meshes = new ArrayList<ParticleMesh>();
    try {
      for (var batch : batches.entrySet()) {
        var type = batch.getKey();
        var pass = new PortalDraw(type.pipeline(), type.outputTarget(), transforms, scissor, PortalDraw.textures(type));
        var builder = new BufferBuilder(storage, type.mode(), type.format());
        if (tracker != null) tracker.delegate = builder;
        var vertices = tracker == null ? builder : tracker;
        for (var submit : batch.getValue()) submit.geometry.render(submit.pose, vertices);
        var mesh = builder.build();
        if (mesh != null) meshes.add(new ParticleMesh(pass, mesh));
      }
      if (meshes.isEmpty()) return;
      var bounds = tracker == null ? ScreenBounds.FULL_SCREEN
          : tracker.project(PortalSceneManager.surfaceProjection().mul(modelView));
      if (bounds == null) {
        meshes.forEach(ParticleMesh::close);
        return;
      }
      var group = new ParticleGroup(submits.getFirst().renderType.outputTarget().getRenderTarget(),
          bounds, scissor, RenderSystem.getProjectionMatrixBuffer(), meshes);
      if (IrisEffectTarget.supportsColorEffects()) execute(group);
      else {
        deferred.add(group);
        IrisEffectTarget.deferDraw(() -> {
          deferred.remove(group);
          execute(group);
        });
      }
    } catch (RuntimeException | Error exception) {
      meshes.forEach(ParticleMesh::close);
      meshes.clear();
      throw exception;
    }
  }

  private record ParticleMesh(PortalDraw pass, MeshData mesh) {
    void close() {
      mesh.close();
    }
  }

  private record ParticleGroup(RenderTarget target, ScreenBounds bounds, ScissorState scissor,
      GpuBufferSlice projection, List<ParticleMesh> meshes) implements AutoCloseable {
    @Override
    public void close() {
      meshes.forEach(ParticleMesh::close);
      meshes.clear();
    }
  }

  @SuppressWarnings("try")
  private void execute(ParticleGroup group) {
    try (group; var scissor = PortalDraw.preserveScissor(); var ignored = IrisEffectTarget.begin()) {
      composite(group);
    }
  }

  private void composite(ParticleGroup group) {
    var previousColor = RenderSystem.outputColorTextureOverride;
    var previousDepth = RenderSystem.outputDepthTextureOverride;
    var target = group.target;
    var color = previousColor != null ? previousColor : target.getColorTextureView();
    var region = group.bounds.area(color.getWidth(0), color.getHeight(0), group.scissor);
    if (region == null) return;

    prepareTargets(color);
    var encoder = RenderSystem.getDevice().createCommandEncoder();
    encoder.copyTextureToTexture(color.texture(), background, 0, region.x(), region.y(),
        region.x(), region.y(), region.width(), region.height());
    try (var clear = encoder.createRenderPass(() -> "Clear portal shot light", lightView, OptionalInt.empty())) {
      clear.setPipeline(CLEAR);
      clear.enableScissor(region.x(), region.y(), region.width(), region.height());
      clear.draw(0, 3);
    }
    try {
      RenderSystem.outputColorTextureOverride = lightView;
      RenderSystem.outputDepthTextureOverride =
          previousDepth != null ? previousDepth : target.getDepthTextureView();
      for (var particle : group.meshes) {
        var geometry = PortalDraw.upload(particle.mesh);
        try (var pass = particle.pass.begin()) {
          pass.setUniform("Projection", group.projection);
          geometry.draw(pass);
        }
      }
    } finally {
      RenderSystem.outputColorTextureOverride = previousColor;
      RenderSystem.outputDepthTextureOverride = previousDepth;
    }
    try (var pass = encoder.createRenderPass(() -> "Portal shot light composite", color, OptionalInt.empty())) {
      pass.setPipeline(COMPOSITE);
      pass.enableScissor(region.x(), region.y(), region.width(), region.height());
      var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
      pass.bindTexture("Background", backgroundView, sampler);
      pass.bindTexture("Light", lightView, sampler);
      pass.draw(0, 3);
    }
  }

  public static void endFrame() {
    INSTANCE.deferred.forEach(ParticleGroup::close);
    INSTANCE.deferred.clear();
  }

  public static void clearResources() {
    endFrame();
    INSTANCE.releaseTargets();
  }

  private record ScreenBounds(float left, float bottom, float right, float top) {
    private static final ScreenBounds FULL_SCREEN = new ScreenBounds(-1, -1, 1, 1);

    PortalScreenRegion area(int width, int height, ScissorState scissor) {
      int x = Math.max(0, (int) Math.floor((left + 1) * .5 * width) - 1);
      int y = Math.max(0, (int) Math.floor((bottom + 1) * .5 * height) - 1);
      int endX = Math.min(width, (int) Math.ceil((right + 1) * .5 * width) + 1);
      int endY = Math.min(height, (int) Math.ceil((top + 1) * .5 * height) + 1);
      if (scissor.enabled()) {
        x = Math.max(x, scissor.x());
        y = Math.max(y, scissor.y());
        endX = Math.min(endX, scissor.x() + scissor.width());
        endY = Math.min(endY, scissor.y() + scissor.height());
      }
      return endX <= x || endY <= y ? null : new PortalScreenRegion(x, y, endX - x, endY - y);
    }
  }

  private static final class BoundsTracker implements VertexConsumer {
    VertexConsumer delegate;
    private float minX = Float.POSITIVE_INFINITY, minY = minX, minZ = minX;
    private float maxX = Float.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;

    ScreenBounds project(Matrix4fc viewProjection) {
      if (minX == Float.POSITIVE_INFINITY) return null;
      float left = Float.POSITIVE_INFINITY, bottom = left;
      float right = Float.NEGATIVE_INFINITY, top = right;
      var clip = new Vector4f();
      for (int corner = 0; corner < 8; corner++) {
        viewProjection.transform(clip.set((corner & 1) == 0 ? minX : maxX,
            (corner & 2) == 0 ? minY : maxY, (corner & 4) == 0 ? minZ : maxZ, 1));
        if (clip.w <= 1e-5f) return ScreenBounds.FULL_SCREEN;
        float x = clip.x / clip.w, y = clip.y / clip.w;
        left = Math.min(left, x);
        bottom = Math.min(bottom, y);
        right = Math.max(right, x);
        top = Math.max(top, y);
      }
      if (right < -1 || top < -1 || left > 1 || bottom > 1) return null;
      return new ScreenBounds(
          Math.max(-1, left), Math.max(-1, bottom), Math.min(1, right), Math.min(1, top));
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
      minX = Math.min(minX, x);
      minY = Math.min(minY, y);
      minZ = Math.min(minZ, z);
      maxX = Math.max(maxX, x);
      maxY = Math.max(maxY, y);
      maxZ = Math.max(maxZ, z);
      delegate.addVertex(x, y, z);
      return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
      delegate.setColor(r, g, b, a);
      return this;
    }

    @Override
    public VertexConsumer setColor(int color) {
      delegate.setColor(color);
      return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
      delegate.setUv(u, v);
      return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
      delegate.setUv1(u, v);
      return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
      delegate.setUv2(u, v);
      return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
      delegate.setNormal(x, y, z);
      return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
      delegate.setLineWidth(width);
      return this;
    }
  }

  private void prepareTargets(GpuTextureView source) {
    int width = source.getWidth(0);
    int height = source.getHeight(0);
    int storageFormat = originalColor == source.texture() ? backgroundFormat : PortalTextures.storageFormat(source);
    if (light != null && light.getWidth(0) == width && light.getHeight(0) == height
        && backgroundFormat == storageFormat) {
      originalColor = source.texture();
      return;
    }

    releaseTargets();
    var device = RenderSystem.getDevice();
    try {
      light = PortalTextures.color("Portal shot light",
          GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING,
          width, height, GL30.GL_RGBA16F);
      lightView = device.createTextureView(light);
      background = PortalTextures.color("Portal shot background",
          GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, width, height, storageFormat);
      backgroundView = device.createTextureView(background);
    } catch (RuntimeException | Error exception) {
      releaseTargets();
      throw exception;
    }
    backgroundFormat = storageFormat;
    originalColor = source.texture();
  }

  private void releaseTargets() {
    if (backgroundView != null) backgroundView.close();
    if (background != null) background.close();
    if (lightView != null) lightView.close();
    if (light != null) light.close();
    backgroundView = null;
    background = null;
    lightView = null;
    light = null;
    originalColor = null;
  }
}
