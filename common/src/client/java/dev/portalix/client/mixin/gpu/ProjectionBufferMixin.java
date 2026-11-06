package dev.portalix.client.mixin.gpu;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ProjectionMatrixBuffer.class)
abstract class ProjectionBufferMixin {
  @Unique private final Matrix4f portalix$uploadedMatrix = new Matrix4f();
  @Unique private GpuBufferSlice portalix$uploadedSlice;

  @WrapOperation(
      method = "getBuffer*",
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;writeBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"),
      require = 2, allow = 2)
  private GpuBufferSlice portalix$unchangedProjection(
      ProjectionMatrixBuffer buffer, Matrix4f matrix, Operation<GpuBufferSlice> original) {
    if (portalix$uploadedSlice != null && portalix$uploadedMatrix.equals(matrix))
      return portalix$uploadedSlice;
    var slice = original.call(buffer, matrix);
    portalix$uploadedMatrix.set(matrix);
    portalix$uploadedSlice = slice;
    return slice;
  }
}
