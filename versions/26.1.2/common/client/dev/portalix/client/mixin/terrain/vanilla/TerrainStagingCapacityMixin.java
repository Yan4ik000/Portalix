package dev.portalix.client.mixin.terrain.vanilla;

import com.mojang.blaze3d.vertex.UberGpuBuffer;
import dev.portalix.client.portal.terrain.TerrainStagingCapacityAccess;
import java.nio.ByteBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(UberGpuBuffer.class)
abstract class TerrainStagingCapacityMixin {
  @Shadow @Final private UberGpuBuffer.UberGpuBufferStagingBuffer stagingBuffer;
  @Shadow private int stagingBufferUsedSize;

  @Inject(method = "addAllocation", at = @At("HEAD"))
  private <T> void portalix$grow(T key, UberGpuBuffer.UploadCallback<T> callback,
      ByteBuffer data, CallbackInfoReturnable<Boolean> cir) {
    if (stagingBuffer instanceof TerrainStagingCapacityAccess capacity) {
      int required = (int) Math.min(Integer.MAX_VALUE, (long) stagingBufferUsedSize + data.remaining());
      capacity.portalix$ensureCapacity(required);
    }
  }
}
