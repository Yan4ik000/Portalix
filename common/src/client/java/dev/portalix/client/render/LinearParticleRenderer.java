package dev.portalix.client.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.portalix.PortalixMod;
import dev.portalix.client.ModResourceReload;
import dev.portalix.client.compat.iris.IrisEffectTarget;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.platform.ClientPlatform.Phase;
import dev.portalix.client.platform.ClientPlatform;
import dev.portalix.client.portal.scene.PortalSceneManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

public final class LinearParticleRenderer
    extends RenderTypeFeatureRenderer<LinearParticleRenderer.Submit> {
  public static final FeatureRendererType<Submit> TYPE =
      FeatureRendererType.create("portalix_shot_light");
  private static final RenderPipeline COMPOSITE =
      RenderPipelines.register(
          RenderPipeline.builder()
              .withLocation(PortalixMod.id("pipeline/shot_light_composite"))
              .withVertexShader("core/screenquad")
              .withFragmentShader(PortalixMod.id("core/shot_light_composite"))
              .withBindGroupLayout(
                  BindGroupLayout.builder().withSampler("Background").withSampler("Light").build())
              .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
              .withColorTargetState(ColorTargetState.DEFAULT)
              .withCull(false)
              .build());

  private TextureTarget light;
  private GpuTexture background;
  private GpuTextureView backgroundView;
  private final List<ScreenBounds> groupBounds = new ArrayList<>();

  public static void submit(
      OrderedSubmitNodeCollector collector,
      PoseStack pose,
      RenderType renderType,
      Identifier texture,
      SubmitNodeCollector.CustomGeometryRenderer geometry) {
    ClientPlatform.INSTANCE.submit(collector,
        Phase.AFTER_TERRAIN,
        new Submit(pose.last().copy(), ParticleRenderTypes.particle(renderType, texture), geometry));
  }

  public record Submit(
      PoseStack.Pose pose,
      RenderType renderType,
      SubmitNodeCollector.CustomGeometryRenderer geometry)
      implements SubmitNode {
    @Override
    public FeatureRendererType<Submit> featureType() {
      return TYPE;
    }
  }

  @Override
  protected void buildGroup(FeatureFrameContext context, List<Submit> submits) {
    var tracker = ModResourceReload.bundledParticleShaders() ? new BoundsTracker() : null;
    for (Submit submit : submits) {
      var vertices = getVertexBuilder(submit.renderType);
      if (tracker != null) {
        tracker.delegate = vertices;
        vertices = tracker;
      }
      submit.geometry.render(submit.pose, vertices);
    }
    groupBounds.add(tracker == null ? ScreenBounds.FULL_SCREEN
        : tracker.project(PortalSceneManager.surfaceProjection().mul(RenderSystem.getModelViewMatrixCopy())));
  }

  @Override
  public void executeGroup(
      FeatureFrameContext context, int groupIndex, List<Submit> submits, boolean strictlyOrdered) {
    if (IrisShaders.shadowPass()) return;
    var bounds = groupBounds.get(groupIndex);
    if (bounds == null) return;
    Runnable particles = () -> super.executeGroup(context, groupIndex, submits, strictlyOrdered);
    var target = submits.getFirst().renderType.outputTarget().getRenderTarget();
    if (!IrisEffectTarget.supportsColorEffects()) {
      var replay = IrisEffectTarget.captureDraws(particles);
      if (replay != null) {
        var scissor = new ScissorState(RenderSystem.getScissorStateForRenderTypeDraws());
        IrisEffectTarget.deferDraw(() -> executeParticles(target, bounds, scissor, replay));
      }
    } else executeParticles(target, bounds, RenderSystem.getScissorStateForRenderTypeDraws(), particles);
  }

  @SuppressWarnings("try")
  private void executeParticles(
      RenderTarget target, ScreenBounds bounds, ScissorState scissor, Runnable particles) {
    try (var ignored = IrisEffectTarget.begin()) {
      composite(target, bounds, scissor, particles);
    }
  }

  @SuppressWarnings("try")
  private void composite(
      RenderTarget target, ScreenBounds bounds, ScissorState scissor, Runnable particles) {
    var previousColor = RenderSystem.outputColorTextureOverride;
    var previousDepth = RenderSystem.outputDepthTextureOverride;
    var color = previousColor != null ? previousColor : target.getColorTextureView();
    var region = bounds.area(color.getWidth(0), color.getHeight(0), scissor);
    if (region == null) return;
    prepareTargets(color.texture());
    var encoder = RenderSystem.getDevice().createCommandEncoder();
    encoder.copyTextureToTexture(
        color.texture(), background, 0, region.x(), region.y(), region.x(), region.y(),
        region.width(), region.height());
    try (var clear = encoder.createRenderPass(() -> "Clear portal shot light", light.getColorTextureView(),
        Optional.of(new Vector4f(0.0F)), null, OptionalDouble.empty(), region)) {}
    try {
      RenderSystem.outputColorTextureOverride = light.getColorTextureView();
      RenderSystem.outputDepthTextureOverride =
          previousDepth != null ? previousDepth : target.getDepthTextureView();
      particles.run();
    } finally {
      RenderSystem.outputColorTextureOverride = previousColor;
      RenderSystem.outputDepthTextureOverride = previousDepth;
    }
    try (var pass =
        encoder.createRenderPass(() -> "Portal shot light composite", color, Optional.empty())) {
      pass.setPipeline(IrisEffectTarget.pipeline(COMPOSITE, color.texture().getFormat()));
      pass.enableScissor(region.x(), region.y(), region.width(), region.height());
      var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
      pass.bindTexture("Background", backgroundView, sampler);
      pass.bindTexture("Light", light.getColorTextureView(), sampler);
      pass.draw(3, 1, 0, 0);
    }
  }

  @Override
  public void finishExecute(FeatureFrameContext context) {
    super.finishExecute(context);
    groupBounds.clear();
  }

  private record ScreenBounds(float left, float bottom, float right, float top) {
    private static final ScreenBounds FULL_SCREEN = new ScreenBounds(-1, -1, 1, 1);

    RenderPass.RenderArea area(int width, int height, ScissorState scissor) {
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
      return endX <= x || endY <= y ? null : new RenderPass.RenderArea(x, y, endX - x, endY - y);
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

  private void prepareTargets(GpuTexture source) {
    int width = source.getWidth(0);
    int height = source.getHeight(0);
    var format = switch (source.getFormat()) {
      case RGB8_UNORM -> GpuFormat.RGBA8_UNORM;
      case RGB8_SNORM -> GpuFormat.RGBA8_SNORM;
      case RGB16_UNORM -> GpuFormat.RGBA16_UNORM;
      case RGB16_SNORM -> GpuFormat.RGBA16_SNORM;
      case RGB16_FLOAT -> GpuFormat.RGBA16_FLOAT;
      case RGB32_FLOAT -> GpuFormat.RGBA32_FLOAT;
      default -> source.getFormat();
    };
    if (light != null
        && light.width == width
        && light.height == height
        && background.getFormat() == format) return;
    releaseTargets();
    light = new TextureTarget("Portal shot light", width, height, false, GpuFormat.RGBA16_FLOAT);
    background =
        RenderSystem.getDevice()
            .createTexture(
                "Portal shot background",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                format,
                width,
                height,
                1,
                1);
    backgroundView = RenderSystem.getDevice().createTextureView(background);
  }

  private void releaseTargets() {
    if (backgroundView != null) backgroundView.close();
    if (background != null) background.close();
    if (light != null) light.destroyBuffers();
    backgroundView = null;
    background = null;
    light = null;
  }

  @Override
  public void close() {
    releaseTargets();
  }
}
