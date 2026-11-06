package dev.portalix.world.chunk;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import java.util.List;

public interface PortalLoadingObservers {
  void portalix$observeLoading(Long2IntMap positions, List<PortalChunkObservers> priorities);
}
