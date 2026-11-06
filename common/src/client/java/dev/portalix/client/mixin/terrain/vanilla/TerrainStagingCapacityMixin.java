package dev.portalix.client.mixin.terrain.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.StagingBuffer;
import dev.portalix.client.portal.terrain.TerrainStagingCapacityAccess;
import java.nio.ByteBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(StagingBuffer.class)
abstract class TerrainStagingCapacityMixin {
  @Shadow private int nextWriteOffset;

  @WrapOperation(
      method = "tryAppend",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/mojang/blaze3d/vertex/StagingBuffer;getWriteBuffer()Ljava/nio/ByteBuffer;"))
  private ByteBuffer portalix$grow(
      StagingBuffer staging, Operation<ByteBuffer> original, ByteBuffer data) {
    if (staging instanceof TerrainStagingCapacityAccess capacity)
      capacity.portalix$ensureCapacity(nextWriteOffset + data.remaining());
    return original.call(staging);
  }
}
