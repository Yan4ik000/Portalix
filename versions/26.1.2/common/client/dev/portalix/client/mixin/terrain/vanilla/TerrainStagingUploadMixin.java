package dev.portalix.client.mixin.terrain.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.portal.terrain.TerrainStagingCapacityAccess;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.mojang.blaze3d.vertex.UberGpuBuffer$UberGpuBufferStagingBuffer$CPUStagingBuffer")
abstract class TerrainStagingUploadMixin implements TerrainStagingCapacityAccess {
  @Shadow @Final @Mutable private ByteBuffer stagingBuffer;
  @Unique private int portalix$maximum;

  @WrapOperation(
      method = "<init>",
      at =
          @At(
              value = "INVOKE",
              target = "Lorg/lwjgl/system/MemoryUtil;memAlloc(I)Ljava/nio/ByteBuffer;"))
  private ByteBuffer portalix$initialCapacity(int size, Operation<ByteBuffer> original) {
    portalix$maximum = size;
    return original.call(Math.min(size, 64 * 1024));
  }

  @Override
  public void portalix$ensureCapacity(int required) {
    int requested = Math.min(required, portalix$maximum);
    if (requested <= stagingBuffer.capacity()) return;
    int capacity = (int) Math.min(portalix$maximum, Long.highestOneBit(requested - 1L) << 1);
    stagingBuffer = MemoryUtil.memRealloc(stagingBuffer.clear(), capacity);
  }
}
