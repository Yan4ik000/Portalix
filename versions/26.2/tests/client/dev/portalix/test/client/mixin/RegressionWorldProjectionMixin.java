package dev.portalix.test.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.portalix.test.client.FrameCapture;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
abstract class RegressionWorldProjectionMixin {
  @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"), allow = 1)
  private GpuBufferSlice captureWorldProjection(ProjectionMatrixBuffer buffer, Matrix4f projection, Operation<GpuBufferSlice> original) {
    GameRenderer renderer = (GameRenderer) (Object) this;
    FrameCapture.worldProjection(projection, renderer.gameRenderState().levelRenderState.cameraRenderState);
    return original.call(buffer, projection);
  }
}
