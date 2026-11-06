package dev.portalix.client.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.portalix.PortalixMod;
import dev.portalix.client.compat.iris.IrisEffectTarget;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.platform.ClientPlatform.Phase;
import dev.portalix.client.platform.ClientPlatform;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
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
    for (Submit submit : submits) {
      submit.geometry.render(submit.pose, getVertexBuilder(submit.renderType));
    }
  }

  @Override
  @SuppressWarnings("try")
  public void executeGroup(
      FeatureFrameContext context, int groupIndex, List<Submit> submits, boolean strictlyOrdered) {
    if (IrisShaders.shadowPass()) return;
    try (var ignored = IrisEffectTarget.begin()) {
      executeParticles(context, groupIndex, submits, strictlyOrdered);
    }
  }

  private void executeParticles(
      FeatureFrameContext context, int groupIndex, List<Submit> submits, boolean strictlyOrdered) {
    var target = submits.getFirst().renderType.outputTarget().getRenderTarget();
    var previousColor = RenderSystem.outputColorTextureOverride;
    var previousDepth = RenderSystem.outputDepthTextureOverride;
    var color = previousColor != null ? previousColor : target.getColorTextureView();
    prepareTargets(color.texture());
    var encoder = RenderSystem.getDevice().createCommandEncoder();
    encoder.copyTextureToTexture(
        color.texture(), background, 0, 0, 0, 0, 0, light.width, light.height);
    encoder.clearColorTexture(light.getColorTexture(), new Vector4f(0.0F));
    try {
      RenderSystem.outputColorTextureOverride = light.getColorTextureView();
      RenderSystem.outputDepthTextureOverride =
          previousDepth != null ? previousDepth : target.getDepthTextureView();
      super.executeGroup(context, groupIndex, submits, strictlyOrdered);
    } finally {
      RenderSystem.outputColorTextureOverride = previousColor;
      RenderSystem.outputDepthTextureOverride = previousDepth;
    }
    try (var pass =
        encoder.createRenderPass(() -> "Portal shot light composite", color, Optional.empty())) {
      pass.setPipeline(IrisEffectTarget.pipeline(COMPOSITE, color.texture().getFormat()));
      if (RenderSystem.getScissorStateForRenderTypeDraws().enabled()) {
        var scissor = RenderSystem.getScissorStateForRenderTypeDraws();
        pass.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
      }
      var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
      pass.bindTexture("Background", backgroundView, sampler);
      pass.bindTexture("Light", light.getColorTextureView(), sampler);
      pass.draw(3, 1, 0, 0);
    }
  }

  private void prepareTargets(GpuTexture source) {
    int width = source.getWidth(0);
    int height = source.getHeight(0);
    if (light != null
        && light.width == width
        && light.height == height
        && background.getFormat() == source.getFormat()) return;
    releaseTargets();
    light = new TextureTarget("Portal shot light", width, height, false, GpuFormat.RGBA16_FLOAT);
    background =
        RenderSystem.getDevice()
            .createTexture(
                "Portal shot background",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                source.getFormat(),
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
