package dev.portalix.client.compat.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger.GeometryPlanes;

public interface SodiumSortPlanesAccess {
  SodiumSortPlanes portalix$sortPlanes();

  void portalix$retainSortPlanes(GeometryPlanes planes);

  SodiumInitialSort portalix$initialSort();

  void portalix$initialSort(SodiumInitialSort initial);
}
