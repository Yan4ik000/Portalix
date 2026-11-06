package dev.portalix.world.chunk;

import it.unimi.dsi.fastutil.longs.Long2IntMap;

public interface LoadingObserverTracker {
  void portalix$observeLoading(Long2IntMap positions);
}
