package dev.portalix.client.mixin.terrain.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.vertex.UberGpuBuffer;
import dev.portalix.client.portal.terrain.TerrainBufferSizing;
import dev.portalix.client.portal.terrain.TerrainStagedAllocationAccess;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(UberGpuBuffer.class)
abstract class TerrainBufferHeapMixin {
  @Shadow @Final private int alignSize;
  @Shadow @Final private Object2ObjectOpenHashMap<?, ?> stagedAllocations;
  @Unique private long portalix$previousHeapSize;

  @WrapOperation(
      method = "uploadStagedAllocations",
      at = @At(value = "NEW", target = "com/mojang/blaze3d/vertex/UberGpuBuffer$UberGpuBufferHeap"))
  private UberGpuBuffer.UberGpuBufferHeap portalix$heapForBatch(
      long maximum,
      GpuDevice device,
      int usage,
      String name,
      Operation<UberGpuBuffer.UberGpuBufferHeap> original) {

    long pending = 256;
    for (var entry : stagedAllocations.values()) {
      long size = ((TerrainStagedAllocationAccess) entry).portalix$buffer().size();
      pending += TerrainBufferSizing.allocationSize(size, alignSize);
    }
    long size = TerrainBufferSizing.nextHeap(maximum, portalix$previousHeapSize, pending);
    var heap = original.call(size, device, usage, name);
    portalix$previousHeapSize = size;
    return heap;
  }
}
