package dev.portalix.client.mixin.terrain.vanilla;

import com.mojang.blaze3d.vertex.TlsfAllocator;
import com.mojang.blaze3d.vertex.UberGpuBuffer;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(UberGpuBuffer.class)
abstract class TerrainAllocationMapMixin {
  @Mutable @Shadow @Final private Map<Object, TlsfAllocator.Allocation> allocationMap;

  @Inject(method = "<init>", at = @At("RETURN"))
  private void portalix$allocationTable(CallbackInfo ci) {
    var allocations = new Object2ObjectOpenHashMap<Object, TlsfAllocator.Allocation>(
        Math.max(256, allocationMap.size()));
    allocations.putAll(allocationMap);
    allocationMap = allocations;
  }
}
