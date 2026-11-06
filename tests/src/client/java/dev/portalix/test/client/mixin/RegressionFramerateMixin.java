package dev.portalix.test.client.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.portalix.test.client.ClientRegressionRunner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FramerateLimitTracker.class)
abstract class RegressionFramerateMixin {
  @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
  private void unlimitedDuringRegression(CallbackInfoReturnable<Integer> callback) {
    if (ClientRegressionRunner.active()) callback.setReturnValue(260);
  }

  @Inject(method = "isHeavilyThrottled", at = @At("HEAD"), cancellable = true)
  private void renderWhileUnfocused(CallbackInfoReturnable<Boolean> callback) {
    if (ClientRegressionRunner.active()) callback.setReturnValue(false);
  }
}
