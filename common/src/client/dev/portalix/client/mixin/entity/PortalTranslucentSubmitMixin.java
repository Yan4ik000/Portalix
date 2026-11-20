package dev.portalix.client.mixin.entity;

import dev.portalix.client.portal.entity.PortalEntityClipping;
import net.minecraft.client.renderer.feature.phase.TranslucentFeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.TranslucentSubmit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TranslucentFeatureRenderPhase.class)
abstract class PortalTranslucentSubmitMixin {
  @Inject(
      method = "submit(Lnet/minecraft/client/renderer/feature/submit/TranslucentSubmit;)V",
      at = @At("HEAD"))
  private void portalix$clip(TranslucentSubmit submit, CallbackInfo ci) {
    PortalEntityClipping.remember(submit);
  }
}
