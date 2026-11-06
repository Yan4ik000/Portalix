package dev.portalix.client.compat.sodium;

import net.caffeinemc.mods.sodium.client.gpu.arena.RegionAllocatorHandle;

public interface SodiumArenaBatch {
  void portalix$beginRelease(RegionAllocatorHandle owner);

  void portalix$endRelease();
}
