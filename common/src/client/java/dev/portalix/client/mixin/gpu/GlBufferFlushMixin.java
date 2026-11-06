package dev.portalix.client.mixin.gpu;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.portalix.client.render.MappedBufferAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlBuffer$Direct$1")
abstract class GlBufferFlushMixin implements MappedBufferAccess {
  @Unique private GpuBufferSlice portalix$range;

  @Override
  public void portalix$mappedRange(GpuBufferSlice range) {
    portalix$range = range;
  }

  @WrapOperation(
      method = "run",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/mojang/blaze3d/opengl/DirectStateAccess;flushMappedBufferRange(IJJI)V"))
  private void portalix$flushRange(
      @Coerce Object dsa,
      int handle,
      long offset,
      long length,
      int usage,
      Operation<Void> original) {
    original.call(dsa, handle, portalix$range.offset(), portalix$range.length(), usage);
  }
}
