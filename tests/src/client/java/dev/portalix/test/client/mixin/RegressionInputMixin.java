package dev.portalix.test.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.portalix.test.client.ClientRegressionRunner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InputConstants.class)
abstract class RegressionInputMixin {
  @Inject(method = {"setupKeyboardCallbacks", "setupMouseCallbacks", "grabOrReleaseMouse"},
      at = @At("HEAD"), cancellable = true)
  private static void isolatePhysicalInput(CallbackInfo callback) {
    if (ClientRegressionRunner.active()) callback.cancel();
  }

  @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
  private static void ignorePhysicalKeys(CallbackInfoReturnable<Boolean> callback) {
    if (ClientRegressionRunner.active()) callback.setReturnValue(false);
  }
}
