package dev.portalix.test.client.mixin;

import com.mojang.blaze3d.platform.SDLEventHandler;
import dev.portalix.test.client.ClientRegressionRunner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SDLEventHandler.class)
abstract class RegressionSdlInputMixin {
  @Inject(method = {"handleKeyEvent", "handleTextInputEvent", "handleTextEditingEvent",
      "handleMouseMotionEvent", "handleMouseButtonEvent", "handleMouseWheelEvent"},
      at = @At("HEAD"), cancellable = true)
  private void isolatePhysicalInput(CallbackInfo callback) {
    if (ClientRegressionRunner.active()) callback.cancel();
  }
}
