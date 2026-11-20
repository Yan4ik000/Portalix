package dev.portalix.world.chunk;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

public interface PortalLoadingObservers {
  WarmColumnAccess portalix$warmColumns();

  void portalix$loadingDelayChanged(ServerPlayer player);

  void portalix$observeLoading(Long2IntMap positions, List<PortalChunkObservers> priorities);
}
