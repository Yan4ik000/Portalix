package dev.portalix.client.mixin.compat.iris;

import dev.portalix.client.portal.scene.PortalSceneManager;
import net.irisshaders.iris.pathways.HandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandRenderer.class)
abstract class IrisPortalHandMixin {
  @Inject(
      method = {"renderSolid", "renderTranslucent"},
      at = @At("HEAD"),
      cancellable = true)
  private void portalix$cameraLocalHand(CallbackInfo ci) {
    if (PortalSceneManager.isPortalView()) ci.cancel();
  }
}
