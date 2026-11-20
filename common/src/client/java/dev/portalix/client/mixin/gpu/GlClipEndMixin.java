package dev.portalix.client.mixin.gpu;

import dev.portalix.client.portal.clip.GlClipState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlCommandEncoder")
abstract class GlClipEndMixin {
  @Inject(method = "submitRenderPass", at = @At("TAIL"))
  private void portalix$endClip(CallbackInfo ci) {
    GlClipState.setEnabled(false);
  }
}
