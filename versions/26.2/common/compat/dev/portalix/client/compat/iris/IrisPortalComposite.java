package dev.portalix.client.compat.iris;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.portalix.PortalixMod;
import dev.portalix.client.ModResourceReload;
import dev.portalix.client.compat.ShaderBackend.PortalDepthTextures;
import dev.portalix.client.mixin.compat.iris.IrisRenderingPipelineAccessor;
import dev.portalix.client.portal.render.PortalScreenRegion;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;

public final class IrisPortalComposite {
  private static final RenderPipeline RESTORE_DEPTH =
      RenderPipelines.register(
          RenderPipeline.builder()
              .withLocation(PortalixMod.id("pipeline/portal_restore_depth"))
              .withVertexShader("core/screenquad")
              .withFragmentShader(PortalixMod.id("core/portal_restore_depth"))
              .withBindGroupLayout(BindGroupLayout.builder().withSampler("WorldDepth").build())
              .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
              .withColorTargetState(
                  new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, 0))
              .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
              .withCull(false)
              .build());
  private static final ScopedValue<Frame> renderFrame = ScopedValue.newInstance();
  private static IrisRenderingPipelineAccessor handPipeline;
  private static GpuTextureView worldDepth;
  private static GpuTextureView handDepth;
  private static GpuTexture occlusion;
  private static GpuTextureView occlusionDepth;

  private IrisPortalComposite() {}

  private static final class Frame {
    final List<Runnable> apertures = new ArrayList<>();
    final List<Runnable> surfaces = new ArrayList<>();
    PortalScreenRegion surfaceRegion;
    PortalScreenRegion openedRegion;
    boolean composited;
  }

  public static void renderLevel(Runnable render) {
    // Draws borrow staged geometry from this invocation of LevelRenderer.render.
    ScopedValue.where(renderFrame, new Frame()).run(render);
  }

  public static void aperture(PortalScreenRegion region, Runnable draw) {
    var frame = renderFrame.get();
    frame.apertures.add(draw);
    frame.surfaceRegion = region.union(frame.surfaceRegion);
  }

  @SuppressWarnings("try")
  public static void beforeHand(IrisRenderingPipelineAccessor pipeline) {
    var frame = renderFrame.get();
    if (frame.apertures.isEmpty()) return;
    var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
    var depth = pipeline.portalix$targets().getDepthTexture();
    int width = Math.min(target.width, Math.min(depth.getWidth(0), occlusion.getWidth(0)));
    int height = Math.min(target.height, Math.min(depth.getHeight(0), occlusion.getHeight(0)));
    var region = ModResourceReload.bundledPortalDepthShaders()
        ? frame.surfaceRegion.clip(width, height) : PortalScreenRegion.full(width, height);
    if (region != null) {
      try (var ignored = IrisEffectFramebuffer.beginDepth(target)) {
        RenderSystem.getDevice()
            .createCommandEncoder()
            .copyTextureToTexture(
                depth, occlusion, 0, region.x(), region.y(),
                region.x(), region.y(), region.width(), region.height());
        frame.apertures.forEach(Runnable::run);
        frame.openedRegion = region;
      }
    }
    frame.apertures.clear();
    frame.surfaceRegion = null;
  }

  @SuppressWarnings("try")
  public static void restoreDepth() {
    var frame = renderFrame.get();
    if (frame.openedRegion == null) return;
    var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
    var region = frame.openedRegion;
    frame.openedRegion = null;
    try (var ignored = IrisEffectFramebuffer.beginDepth(target)) {
      var color = target.getColorTextureView();
      var depth = target.getDepthTextureView();
      region = region.clip(Math.min(color.getWidth(0), depth.getWidth(0)),
          Math.min(color.getHeight(0), depth.getHeight(0)));
      if (region == null) return;
      try (var pass =
          RenderSystem.getDevice()
              .createCommandEncoder()
              .createRenderPass(
                  () -> "Portal world depth restore",
                  color,
                  Optional.empty(),
                  depth,
                  OptionalDouble.empty())) {
        pass.setPipeline(IrisEffectTarget.pipeline(RESTORE_DEPTH, color.texture().getFormat()));
        pass.bindTexture(
            "WorldDepth",
            occlusionDepth,
            RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
        pass.enableScissor(region.x(), region.y(), region.width(), region.height());
        pass.draw(3, 1, 0, 0);
      }
    }
  }

  public static PortalDepthTextures portalDepthTextures(boolean opensDepth) {
    var pipeline = (IrisRenderingPipelineAccessor) Iris.getPipelineManager().getPipelineNullable();
    var targets = pipeline.portalix$targets();
    var world = targets.getDepthTextureNoHand();
    var hand = targets.getDepthTextureNoTranslucents();
    if (worldDepth == null || worldDepth.texture() != world || handDepth.texture() != hand) {
      closeDepth();
      worldDepth = RenderSystem.getDevice().createTextureView(world);
      handDepth = RenderSystem.getDevice().createTextureView(hand);
      handPipeline = pipeline;
    }
    if (opensDepth && occlusion == null) {
      occlusion =
          RenderSystem.getDevice()
              .createTexture(
                  "Portal occlusion depth",
                  GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                  world.getFormat(),
                  world.getWidth(0),
                  world.getHeight(0),
                  1,
                  1);
      occlusionDepth = RenderSystem.getDevice().createTextureView(occlusion);
    }
    var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    return new PortalDepthTextures(
        new PreparedRenderType.Texture("PortalWorldDepth", worldDepth, sampler),
        new PreparedRenderType.Texture("PortalHandDepth", handDepth, sampler),
        new PreparedRenderType.Texture(
            "PortalOcclusionDepth", opensDepth ? occlusionDepth : worldDepth, sampler));
  }

  private static void closeDepth() {
    if (worldDepth != null) worldDepth.close();
    if (handDepth != null) handDepth.close();
    if (occlusionDepth != null) occlusionDepth.close();
    if (occlusion != null) occlusion.close();
    worldDepth = null;
    handDepth = null;
    occlusion = null;
    occlusionDepth = null;
    handPipeline = null;
  }

  public static void release(IrisRenderingPipelineAccessor pipeline) {
    if (handPipeline == pipeline) closeDepth();
  }

  public static void submit(Runnable draw) {
    var frame = renderFrame.isBound() ? renderFrame.get() : null;
    if (frame != null && !frame.composited && IrisShaders.active()) frame.surfaces.add(draw);
    else draw.run();
  }

  public static void finish() {
    var frame = renderFrame.get();
    frame.composited = true;
    frame.surfaces.forEach(Runnable::run);
  }

  public static void clear() {
    closeDepth();
  }
}
