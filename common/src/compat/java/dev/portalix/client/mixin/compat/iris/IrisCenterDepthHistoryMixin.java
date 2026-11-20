package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.portalix.client.compat.iris.IrisScene;
import dev.portalix.client.compat.iris.IrisViewHistory;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.pathways.CenterDepthSampler;
import org.lwjgl.opengl.GL11C;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CenterDepthSampler.class)
abstract class IrisCenterDepthHistoryMixin implements IrisViewHistory {
  @Shadow @Final private int altTexture;
  @Shadow private boolean everRetrieved;

  @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE", target =
      "Lcom/mojang/blaze3d/opengl/GlStateManager;_genTexture()I"), require = 2, allow = 2)
  private int portalix$trackDepthSample(int texture) {
    return IrisScene.trackTexture(texture);
  }

  @ModifyExpressionValue(method = "<init>", at = @At(value = "NEW", target =
      "net/irisshaders/iris/gl/framebuffer/GlFramebuffer"), allow = 1)
  private GlFramebuffer portalix$trackDepthFramebuffer(GlFramebuffer framebuffer) {
    IrisScene.trackResource(framebuffer::destroy);
    return framebuffer;
  }

  @Inject(method = "setUsage", at = @At("HEAD"), allow = 1)
  private void portalix$initializeDepth(boolean used, CallbackInfo ci) {
    if (used && !everRetrieved) portalix$clearDepth();
  }

  @Override
  public void portalix$invalidateHistory() {
    if (everRetrieved) portalix$clearDepth();
  }

  @Unique
  private void portalix$clearDepth() {
    // Iris 1.11.4 stores the raw reversed-Z depth in this texture.
    int previous = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
    GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, altTexture);
    GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, 0, 1, 1,
        GL11C.GL_RED, GL11C.GL_FLOAT, new float[] {0.0F});
    GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previous);
  }
}
