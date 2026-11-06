package dev.portalix.client.mixin.gpu;

import org.lwjgl.opengl.GL33C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
abstract class GlClipEndMixin {
  @Inject(method = "submitRenderPass", at = @At("TAIL"))
  private void portalix$endClip(CallbackInfo ci) {
    GL33C.glDisable(GL33C.GL_CLIP_DISTANCE0);
    GL33C.glDisable(GL33C.GL_CLIP_DISTANCE1);
  }
}
