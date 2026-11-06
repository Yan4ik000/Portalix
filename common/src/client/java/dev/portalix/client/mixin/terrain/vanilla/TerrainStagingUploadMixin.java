package dev.portalix.client.mixin.terrain.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import dev.portalix.client.portal.terrain.TerrainStagingCapacityAccess;
import dev.portalix.client.render.TransientStagingAccess;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.mojang.blaze3d.vertex.StagingBuffer$Cpu")
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
    if (required <= stagingBuffer.capacity() || required > portalix$maximum) return;
    int capacity = (int) Math.min(portalix$maximum, Long.highestOneBit(required - 1L) << 1);
    stagingBuffer = MemoryUtil.memRealloc(stagingBuffer.clear(), capacity);
  }

  @WrapOperation(
      method = "copyTo",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/mojang/blaze3d/systems/CommandEncoder;writeToBuffer(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Ljava/nio/ByteBuffer;)V"))
  private void portalix$upload(
      CommandEncoder encoder,
      GpuBufferSlice destination,
      ByteBuffer data,
      Operation<Void> original) {
    var memory = encoder.transientMemory();
    long blockSize = ((TransientStagingAccess) memory).portalix$stagingBlockSize();
    int size = data.remaining();
    for (int offset = 0; offset < size; ) {
      int length = (int) Math.min(size - offset, blockSize);
      var chunk = length == size ? data : data.slice(data.position() + offset, length);
      var target =
          length == size
              ? destination
              : new GpuBufferSlice(destination.buffer(), destination.offset() + offset, length);
      var staging = memory.uploadStaging(chunk, 4, GpuBuffer.USAGE_COPY_SRC);
      encoder.copyToBuffer(staging, target);
      offset += length;
    }
  }
}
