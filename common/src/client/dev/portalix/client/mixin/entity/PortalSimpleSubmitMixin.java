package dev.portalix.client.mixin.entity;

import dev.portalix.client.portal.entity.PortalEntityClipping;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SimpleFeatureRenderPhase.class)
abstract class PortalSimpleSubmitMixin {
  @Inject(method = "submit", at = @At("HEAD"))
  private void portalix$clip(SubmitNode submit, CallbackInfo ci) {
    PortalEntityClipping.remember(submit);
  }
}
