package dev.portalix.client.mixin.gpu;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.portalix.client.render.MappedBufferAccess;
import java.nio.ByteBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlBuffer$Direct")
abstract class GlBufferMapMixin {
  @WrapOperation(
      method = "map",
      at = @At(value = "NEW", target = "com/mojang/blaze3d/buffers/GpuBufferSlice$MappedView"))
  private GpuBufferSlice.MappedView portalix$range(
      GpuBufferSlice slice,
      ByteBuffer data,
      Runnable close,
      Operation<GpuBufferSlice.MappedView> original) {
    ((MappedBufferAccess) close).portalix$mappedRange(slice);
    return original.call(slice, data, close);
  }
}
