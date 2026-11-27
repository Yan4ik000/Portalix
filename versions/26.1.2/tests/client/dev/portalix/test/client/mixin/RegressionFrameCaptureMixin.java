package dev.portalix.test.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.portalix.test.client.ClientRegressionRunner;
import dev.portalix.test.client.FrameCapture;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class RegressionFrameCaptureMixin {
  @Inject(method = "renderFrame", at = @At("HEAD"))
  private void portalix$beginFrame(boolean advanceGameTime, CallbackInfo callback) {
    FrameCapture.beginFrame();
  }

  @WrapOperation(method = "renderFrame", at = @At(value = "INVOKE",
      target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen()V"))
  private void portalix$capturePresentedFrame(RenderTarget target, Operation<Void> original) {
    ClientRegressionRunner.frameRendered(target);
    original.call(target);
  }
}
