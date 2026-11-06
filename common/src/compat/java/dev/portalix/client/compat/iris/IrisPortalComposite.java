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
import dev.portalix.client.mixin.compat.iris.IrisRenderingPipelineAccessor;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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
  private static final Map<RenderTarget, Frame> frames = new IdentityHashMap<>();
  private static IrisRenderingPipelineAccessor handPipeline;
  private static GpuTextureView worldDepth;
  private static GpuTextureView handDepth;
  private static GpuTexture occlusion;
  private static GpuTextureView occlusionDepth;

  private IrisPortalComposite() {}

  private static final class Frame {
    final List<Runnable> apertures = new ArrayList<>();
    final List<Runnable> surfaces = new ArrayList<>();
    boolean depthOpened;
  }

  private static Frame frame() {
    var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
    return frames.computeIfAbsent(target, ignored -> new Frame());
  }

  public static void aperture(Runnable draw) {
    frame().apertures.add(draw);
  }

  public static void beforeHand(IrisRenderingPipelineAccessor pipeline) {
    var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
    var frame = frames.get(target);
    if (frame == null || frame.apertures.isEmpty()) return;
    var depth = pipeline.portalix$targets().getDepthTexture();
    RenderSystem.getDevice()
        .createCommandEncoder()
        .copyTextureToTexture(
            depth, occlusion, 0, 0, 0, 0, 0, depth.getWidth(0), depth.getHeight(0));
    frame.apertures.forEach(Runnable::run);
    frame.apertures.clear();
    frame.depthOpened = true;
  }

  @SuppressWarnings("try")
  public static void restoreDepth() {
    var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
    var frame = frames.get(target);
    if (frame == null || !frame.depthOpened) return;
    frame.depthOpened = false;
    var framebuffer = IrisEffectTarget.current();
    try (var ignored = IrisEffectTarget.begin()) {
      var color = framebuffer.getColorTextureView();
      var depth = framebuffer.getDepthTextureView();
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
        pass.draw(3, 1, 0, 0);
      }
    }
  }

  public static List<PreparedRenderType.Texture> handTextures() {
    var pipeline = (IrisRenderingPipelineAccessor) Iris.getPipelineManager().getPipelineNullable();
    var targets = pipeline.portalix$targets();
    var world = targets.getDepthTextureNoHand();
    var hand = targets.getDepthTextureNoTranslucents();
    if (worldDepth == null || worldDepth.texture() != world || handDepth.texture() != hand) {
      closeDepth();
      worldDepth = RenderSystem.getDevice().createTextureView(world);
      handDepth = RenderSystem.getDevice().createTextureView(hand);
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
      handPipeline = pipeline;
    }
    var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    return List.of(
        new PreparedRenderType.Texture("PortalWorldDepth", worldDepth, sampler),
        new PreparedRenderType.Texture("PortalHandDepth", handDepth, sampler),
        new PreparedRenderType.Texture("PortalOcclusionDepth", occlusionDepth, sampler));
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
    frame().surfaces.add(draw);
  }

  public static void finish() {
    var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
    var pending = frames.remove(target);
    if (pending != null) pending.surfaces.forEach(Runnable::run);
  }

  public static void clear() {
    frames.clear();
    closeDepth();
  }
}
