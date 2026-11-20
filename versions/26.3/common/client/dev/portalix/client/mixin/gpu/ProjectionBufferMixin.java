package dev.portalix.client.mixin.gpu;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ProjectionMatrixBuffer.class)
abstract class ProjectionBufferMixin {
  @Unique private final Matrix4f portalix$uploadedMatrix = new Matrix4f();
  @Unique private GpuBufferSlice portalix$uploadedSlice;

  @WrapMethod(method = "writeBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;",
      require = 1, allow = 1)
  private GpuBufferSlice portalix$unchangedProjection(
      Matrix4f matrix, Operation<GpuBufferSlice> original) {
    if (portalix$uploadedSlice != null && portalix$uploadedMatrix.equals(matrix))
      return portalix$uploadedSlice;
    var slice = original.call(matrix);
    portalix$uploadedMatrix.set(matrix);
    portalix$uploadedSlice = slice;
    return slice;
  }
}
