package dev.portalix.client.compat.iris;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.portalix.client.compat.RenderScope;
import dev.portalix.client.mixin.compat.iris.IrisRenderingPipelineAccessor;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.texture.ShaderDataType;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.client.Minecraft;

public final class IrisEffectFramebuffer {
  private static final Map<IrisRenderingPipeline, Map<Integer, Target>> targets =
      new IdentityHashMap<>();
  private IrisEffectFramebuffer() {}

  public static boolean supportsColorEffects() {
    if (!IrisShaders.active() || ImmediateState.bypass || RenderSystem.outputColorTextureOverride != null)
      return true;
    if (!(Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline iris)
        || !iris.shouldOverrideShaders()) return true;
    var targets = ((IrisRenderingPipelineAccessor) iris).portalix$targets();
    var image = findImage(targets, colorAttachment(iris));
    var depth = targets.getDepthTexture();
    // Scaled or integer attachments need the post-composite color/depth pair.
    return image.getInternalFormat().getShaderDataType() == ShaderDataType.FLOAT
        && image.getWidth() == depth.getWidth(0) && image.getHeight() == depth.getHeight(0);
  }

  public static RenderTarget current() {
    if (!IrisShaders.active()) return Minecraft.getInstance().getMainRenderTarget();
    var pipeline = Iris.getPipelineManager().getPipelineNullable();
    if (!(pipeline instanceof IrisRenderingPipeline iris) || !iris.shouldOverrideShaders())
      return Minecraft.getInstance().getMainRenderTarget();
    var images = targets.computeIfAbsent(iris, ignored -> new HashMap<>());
    var access = (IrisRenderingPipelineAccessor) iris;
    int id = colorAttachment(iris);
    var target = images.get(id);
    var image = target == null ? findImage(access.portalix$targets(), id) : target.image;
    var depth = access.portalix$targets().getDepthTexture();
    if (target == null || target.getDepthTexture() != depth
        || target.width != image.getWidth() || target.height != image.getHeight()) {
      if (target != null) target.destroyBuffers();
      target = new Target(id, image, depth);
      images.put(id, target);
    }
    target.used = true;
    return target;
  }

  private static int colorAttachment(IrisRenderingPipeline iris) {
    var access = (IrisRenderingPipelineAccessor) iris;
    var framebuffer = iris.isBeforeTranslucent
        ? access.portalix$beforeTranslucents() : access.portalix$afterTranslucents();
    return framebuffer.getColorAttachment(0);
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
    return new Scope(RenderSystem.outputColorTextureOverride == null ? current() : null);
  }

  static Scope beginDepth(RenderTarget target) {
    return new Scope(target);
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
      if (target != null) {
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
    private boolean used;

    Target(int id, net.irisshaders.iris.targets.RenderTarget image, GpuTexture depth) {
      super("Iris effect target", true);
      this.image = image;
      width = image.getWidth();
      height = image.getHeight();
      colorTexture = new BorrowedTexture(id, width, height);
      colorTextureView = RenderSystem.getDevice().createTextureView(colorTexture);
      depthTexture = depth;
      depthTextureView = RenderSystem.getDevice().createTextureView(depth);
    }

    @Override
    public void destroyBuffers() {
      if (depthTextureView != null) depthTextureView.close();
      if (colorTextureView != null) colorTextureView.close();
      if (colorTexture != null) colorTexture.close();
      depthTextureView = null;
      colorTextureView = null;
      colorTexture = null;
      depthTexture = null;
    }
  }

  private static final class BorrowedTexture extends GlTexture {
    BorrowedTexture(int id, int width, int height) {
      // The texture's actual storage and format are owned by Iris. This view supplies its
      // dimensions and color aspect to the Minecraft command encoder without reallocating it.
      super(USAGE_COPY_SRC | USAGE_TEXTURE_BINDING | USAGE_RENDER_ATTACHMENT,
          "Iris scene color", TextureFormat.RGBA8, width, height, 1, 1, id);
    }

    @Override
    public void close() {
      // The GpuTextureView owns the framebuffer cache in this version.
      closed = true;
    }

    @Override public void addViews() {}
    @Override public void removeViews() {}
  }
}
