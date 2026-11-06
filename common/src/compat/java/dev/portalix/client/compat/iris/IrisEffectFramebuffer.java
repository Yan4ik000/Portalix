package dev.portalix.client.compat.iris;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.opengl.FrameBufferCache;
import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.portalix.client.compat.RenderScope;
import dev.portalix.client.mixin.compat.iris.GlDeviceAccessor;
import dev.portalix.client.mixin.compat.iris.IrisRenderingPipelineAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.mixin.GpuDeviceAccessor;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.client.Minecraft;

public final class IrisEffectFramebuffer {
  private static final Map<IrisRenderingPipeline, Map<Integer, Target>> targets =
      new IdentityHashMap<>();
  private IrisEffectFramebuffer() {}

  public static RenderTarget current() {
    if (!IrisShaders.active()) return Minecraft.getInstance().gameRenderer.mainRenderTarget();
    var pipeline = Iris.getPipelineManager().getPipelineNullable();
    if (!(pipeline instanceof IrisRenderingPipeline iris) || !iris.shouldOverrideShaders())
      return Minecraft.getInstance().gameRenderer.mainRenderTarget();
    var images = targets.computeIfAbsent(iris, ignored -> new HashMap<>());
    var access = (IrisRenderingPipelineAccessor) iris;
    var framebuffer =
        iris.isBeforeTranslucent
            ? access.portalix$beforeTranslucents()
            : access.portalix$afterTranslucents();
    int id = framebuffer.getColorAttachment(0);
    var target = images.get(id);
    var image = target == null ? findImage(access.portalix$targets(), id) : target.image;
    if (target == null || target.width != image.getWidth() || target.height != image.getHeight()) {
      if (target != null) target.destroyBuffers();
      GpuFormat format = null;
      int internal = image.getInternalFormat().getGlFormat();
      for (GpuFormat candidate : GpuFormat.values()) {
        if (GlConst.toGlInternalId(candidate) == internal) {
          format = candidate;
          break;
        }
      }
      if (format == null)
        throw new IllegalStateException("Unsupported Iris color format: " + internal);
      var backend = ((GpuDeviceAccessor) RenderSystem.getDevice()).getBackend();
      var cache = ((GlDeviceAccessor) backend).portalix$framebuffers();
      target = new Target(id, format, image, cache);
      images.put(id, target);
    }
    target.depth(access.portalix$targets().getDepthTexture());
    target.used = true;
    return target;
  }

  private static net.irisshaders.iris.targets.RenderTarget findImage(RenderTargets targets, int texture) {
    for (int index = 0; index < targets.getRenderTargetCount(); index++) {
      var image = targets.get(index);
      if (image != null && (image.getMainTexture() == texture || image.getAltTexture() == texture))
        return image;
    }
    throw new IllegalStateException("Iris default framebuffer texture has no render target owner: " + texture);
  }

  public static Scope begin() {
    if (!IrisShaders.active() || ImmediateState.bypass) return null;
    return new Scope(current());
  }

  public static void clear() {
    for (var images : targets.values())
      for (Target target : images.values()) target.destroyBuffers();
    targets.clear();

  }

  public static void release(WorldRenderingPipeline pipeline) {
    var images = targets.remove(pipeline);
    if (images != null) images.values().forEach(Target::destroyBuffers);
  }

  public static void endFrame() {
    targets
        .values()
        .removeIf(
            images -> {
              images
                  .values()
                  .removeIf(
                      target -> {
                        if (target.used) {
                          target.used = false;
                          return false;
                        }
                        target.destroyBuffers();
                        return true;
                      });
              return images.isEmpty();
            });
  }

  public static final class Scope implements RenderScope {
    private final GpuTextureView color = RenderSystem.outputColorTextureOverride;
    private final GpuTextureView depth = RenderSystem.outputDepthTextureOverride;
    private final boolean bypass = ImmediateState.bypass;
    private final boolean multiply = ImmediateState.safeToMultiply;

    Scope(RenderTarget target) {
      if (color == null) {
        RenderSystem.outputColorTextureOverride = target.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = target.getDepthTextureView();
      }
      ImmediateState.bypass = true;
      ImmediateState.safeToMultiply = false;
    }

    @Override
    public void close() {
      RenderSystem.outputColorTextureOverride = color;
      RenderSystem.outputDepthTextureOverride = depth;
      ImmediateState.bypass = bypass;
      ImmediateState.safeToMultiply = multiply;
    }
  }

  private static final class Target extends RenderTarget {
    private final net.irisshaders.iris.targets.RenderTarget image;
    private GpuTextureView borrowedDepth;
    private boolean used;

    Target(
        int id, GpuFormat format, net.irisshaders.iris.targets.RenderTarget image,
        FrameBufferCache cache) {
      super("Iris effect target", true, format);
      this.image = image;
      width = image.getWidth();
      height = image.getHeight();
      colorTexture = new BorrowedTexture(id, format, width, height, cache);
      colorTextureView = RenderSystem.getDevice().createTextureView(colorTexture);
    }

    void depth(GpuTexture texture) {
      depthTexture = texture;
    }

    @Override
    public GpuTextureView getDepthTextureView() {
      if (borrowedDepth == null || borrowedDepth.texture() != depthTexture) {
        if (borrowedDepth != null) borrowedDepth.close();
        borrowedDepth = RenderSystem.getDevice().createTextureView(depthTexture);
      }
      return borrowedDepth;
    }

    @Override
    public void destroyBuffers() {
      if (borrowedDepth != null) borrowedDepth.close();
      if (colorTextureView != null) colorTextureView.close();
      if (colorTexture != null) colorTexture.close();
      borrowedDepth = null;
      colorTextureView = null;
      colorTexture = null;
      depthTexture = null;
    }
  }

  private static final class BorrowedTexture extends GlTexture {
    private final FrameBufferCache cache;
    private final List<FrameBufferCache.CacheKey> framebuffers = new ArrayList<>();

    BorrowedTexture(int id, GpuFormat format, int width, int height, FrameBufferCache cache) {
      super(
          USAGE_COPY_SRC | USAGE_TEXTURE_BINDING | USAGE_RENDER_ATTACHMENT,
          "Iris scene color",
          format,
          width,
          height,
          1,
          1,
          id,
          cache);
      this.cache = cache;
    }

    @Override
    public void addAssociatedFbo(FrameBufferCache.CacheKey key) {
      framebuffers.add(key);
    }

    @Override
    public void removeAssociatedFbo(FrameBufferCache.CacheKey key) {
      framebuffers.remove(key);
    }

    @Override
    public void close() {
      for (var key : List.copyOf(framebuffers)) cache.destroyFbo(key);
      framebuffers.clear();
      closed = true;
    }

    @Override
    public void addViews() {}

    @Override
    public void removeViews() {}
  }
}
