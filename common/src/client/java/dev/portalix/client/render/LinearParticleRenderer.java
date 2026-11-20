package dev.portalix.client.render;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.portalix.PortalixMod;
import dev.portalix.client.ModResourceReload;
import dev.portalix.client.compat.iris.IrisEffectTarget;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.platform.ClientPlatform.Phase;
import dev.portalix.client.platform.ClientPlatform;
import dev.portalix.client.portal.render.PortalLevelDraws;
import dev.portalix.client.portal.render.PortalScreenRegion;
import dev.portalix.client.portal.scene.PortalSceneManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

public final class LinearParticleRenderer
    implements FeatureRenderer<LinearParticleRenderer.Submit> {
  public static final FeatureRendererType<Submit> TYPE =
      FeatureRendererType.create("portalix_shot_light");
  private static final RenderPipeline COMPOSITE =
      RenderPipelines.register(
          RenderPipeline.builder()
              .withLocation(PortalixMod.id("pipeline/shot_light_composite"))
              .withVertexShader("core/screenquad")
              .withFragmentShader(PortalixMod.id("core/shot_light_composite"))
              .withBindGroupLayout(
                  BindGroupLayout.builder()
                      .withUniform("Background", UniformType.COMBINED_IMAGE_SAMPLER)
                      .withUniform("Light", UniformType.COMBINED_IMAGE_SAMPLER)
                      .build())
              .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
              .withColorTargetState(ColorTargetState.DEFAULT)
              .withCull(false)
              .build());

  private TextureTarget light;
  private GpuTexture background;
  private GpuTextureView backgroundView;
  private final List<Group> groups = new ArrayList<>();
  private final List<OitGroup> oitGroups = new ArrayList<>();

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
      implements SubmitNode {
    @Override
    public FeatureRendererType<Submit> featureType() {
      return TYPE;
    }
  }

  @Override
  public void prepareGroup(FeatureFrameContext context, List<Submit> submits, boolean strictlyOrdered) {
    var tracker = ModResourceReload.bundledParticleShaders() ? new BoundsTracker() : null;
    var buffer = context.stagedVertexBuffer();
    var batches = new LinkedHashMap<RenderType, Batch>();
    for (Submit submit : submits) {
      var batch = batches.computeIfAbsent(submit.renderType, material -> new Batch(
          material.prepare(), buffer.appendDraw(material.format(), material.primitiveTopology())));
      var vertices = buffer.getVertexBuilder(batch.geometry);
      if (tracker != null) {
        tracker.delegate = vertices;
        vertices = tracker;
      }
      submit.geometry.render(submit.pose, vertices);
    }
    for (var batch : batches.values()) buffer.requestIndexCount(batch.geometry);
    var bounds = tracker == null ? ScreenBounds.FULL_SCREEN
        : tracker.project(PortalSceneManager.surfaceProjection().mul(RenderSystem.getModelViewMatrixCopy()));
    groups.add(new Group(bounds, new ArrayList<>(batches.values())));
  }

  @Override
  public void executeGroup(
      FeatureFrameContext context, OitStage stage, RenderPass pass, int groupIndex,
      List<Submit> submits, boolean strictlyOrdered) {
    if (IrisShaders.shadowPass() || stage != null && stage != OitStage.ACCUMULATE) return;
    var group = groups.get(groupIndex);
    if (group.bounds == null) return;
    var draws = new ArrayList<Draw>(group.batches.size());
    for (var batch : group.batches) {
      var info = context.stagedVertexBuffer().getExecuteInfo(batch.geometry);
      if (info != null) draws.add(new Draw(batch.material, info));
    }
    if (draws.isEmpty()) return;

    var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
    var scissor = new ScissorState(RenderSystem.getScissorStateForRenderTypeDraws());
    if (stage == OitStage.ACCUMULATE) {
      var area = group.bounds.area(target.width, target.height, scissor);
      if (area != null) {
        if (oitGroups.isEmpty()) PortalLevelDraws.oitLight(this::drawOit);
        oitGroups.add(new OitGroup(area, draws));
      }
      return;
    }

    PortalLevelDraws.afterTerrain(() -> {
      if (IrisEffectTarget.supportsColorEffects()) {
        executeParticles(target, group.bounds, scissor, draws);
      } else {
        var projection = RenderSystem.getProjectionMatrixBuffer();
        var projectionType = RenderSystem.getProjectionType();
        IrisEffectTarget.deferDraw(() -> {
          var previous = RenderSystem.getProjectionMatrixBuffer();
          var previousType = RenderSystem.getProjectionType();
          RenderSystem.setProjectionMatrix(projection, projectionType);
          try {
            executeParticles(target, group.bounds, scissor, draws);
          } finally {
            RenderSystem.setProjectionMatrix(previous, previousType);
          }
        });
      }
    });
  }

  @SuppressWarnings("try")
  private void executeParticles(
      RenderTarget target, ScreenBounds bounds, ScissorState scissor, List<Draw> particles) {
    try (var ignored = IrisEffectTarget.begin()) {
      composite(target, bounds, scissor, particles);
    }
  }

  private void composite(
      RenderTarget target, ScreenBounds bounds, ScissorState scissor, List<Draw> particles) {
    var color = IrisEffectTarget.color(target);
    var region = bounds.area(color.getWidth(0), color.getHeight(0), scissor);
    if (region == null) return;
    prepareTargets(color.texture());
    var encoder = RenderSystem.getDevice().createCommandEncoder();
    try (var pass = encoder.createRenderPass(() -> "Portal shot light", light.getColorTextureView(),
        Optional.of(new Vector4f(0.0F)), IrisEffectTarget.depth(target), OptionalDouble.empty(), region)) {
      for (var particle : particles) {
        particle.material.drawFromBuffer(particle.geometry, pass);
      }
    }
    compositeLight(color, region);
  }

  private void drawOit(PortalLevelDraws.OitInputs inputs) {
    var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
    var color = target.getColorTextureView();
    PortalScreenRegion bounds = null;
    for (var group : oitGroups) {
      var area = group.area;
      bounds = new PortalScreenRegion(area.x(), area.y(), area.width(), area.height()).union(bounds);
    }
    var region = new RenderPass.RenderArea(bounds.x(), bounds.y(), bounds.width(), bounds.height());
    prepareTargets(color.texture());
    var encoder = RenderSystem.getDevice().createCommandEncoder();
    try (var pass = encoder.createRenderPass(() -> "Portal shot light with transparency",
        light.getColorTextureView(), Optional.of(new Vector4f()), inputs.opaqueDepth(),
        OptionalDouble.empty(), region)) {
      var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
      pass.setUniform("DepthBoundsSampler", inputs.depthBounds(), sampler);
      for (int i = 0; i < inputs.transmittance().size(); i++) {
        pass.setUniform("Coeff" + i, inputs.transmittance().get(i), sampler);
      }
      for (var group : oitGroups) {
        for (var particle : group.particles) {
          particle.material.drawFromBufferOit(particle.geometry, OitStage.ACCUMULATE, pass);
        }
      }
    }
    PortalLevelDraws.afterTerrain(() -> compositeLight(color, region));
  }

  private void compositeLight(GpuTextureView color, RenderPass.RenderArea region) {
    var encoder = RenderSystem.getDevice().createCommandEncoder();
    encoder.copyTextureToTexture(
        color.texture(), background, 0, region.x(), region.y(), region.x(), region.y(),
        region.width(), region.height());
    try (var pass =
        encoder.createRenderPass(() -> "Portal shot light composite", color, Optional.empty())) {
      pass.setPipeline(RenderSystem.getCompiledPipeline(
          IrisEffectTarget.pipeline(COMPOSITE, color.texture().getFormat())));
      pass.enableScissor(region.x(), region.y(), region.width(), region.height());
      var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
      pass.setUniform("Background", backgroundView, sampler);
      pass.setUniform("Light", light.getColorTextureView(), sampler);
      pass.draw(3, 1, 0, 0);
    }
  }

  @Override
  public void finishExecute(FeatureFrameContext context) {
    groups.clear();
    oitGroups.clear();
  }

  private record Batch(PreparedRenderType material, StagedVertexBuffer.Draw geometry) {}

  private record Draw(PreparedRenderType material, StagedVertexBuffer.ExecuteInfo geometry) {}

  private record Group(ScreenBounds bounds, List<Batch> batches) {}

  private record OitGroup(RenderPass.RenderArea area, List<Draw> particles) {}

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
    public VertexConsumer setUv3(float u, float v) {
      delegate.setUv3(u, v);
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
    light = new TextureTarget("Portal shot light", width, height, GpuFormat.RGBA16_FLOAT, null);
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
