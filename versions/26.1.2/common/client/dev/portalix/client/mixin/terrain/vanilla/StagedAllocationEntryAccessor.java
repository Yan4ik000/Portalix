package dev.portalix.client.mixin.terrain.vanilla;

import dev.portalix.client.portal.terrain.TerrainStagedAllocationAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.mojang.blaze3d.vertex.UberGpuBuffer$StagedAllocationEntry")
interface StagedAllocationEntryAccessor extends TerrainStagedAllocationAccess {
  @Override
  @Accessor("size")
  long portalix$size();
}
