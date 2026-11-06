package dev.portalix.test.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.textures.GpuTextureView;
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
  private void beginFrame(boolean advanceGameTime, CallbackInfo callback) {
    FrameCapture.beginFrame();
  }

  @WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target =
      "Lcom/mojang/blaze3d/systems/GpuSurface;blitFromTexture(Lcom/mojang/blaze3d/systems/CommandEncoder;Lcom/mojang/blaze3d/textures/GpuTextureView;)V"), allow = 1)
  private void captureWindowImage(GpuSurface surface, CommandEncoder encoder, GpuTextureView texture, Operation<Void> original) {
    Minecraft client = (Minecraft) (Object) this;
    ClientRegressionRunner.frameRendered(client.gameRenderer.mainRenderTarget());
    original.call(surface, encoder, texture);
  }
}
