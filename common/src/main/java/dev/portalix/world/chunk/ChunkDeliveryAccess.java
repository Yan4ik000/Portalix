package dev.portalix.world.chunk;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ServerLevel;

public interface ChunkDeliveryAccess {
  boolean portalix$watching(ServerLevel level, long chunk);

  boolean portalix$delivered(ServerLevel level, long chunk);

  LongSet portalix$presentColumns(ServerLevel level);

  boolean portalix$retain(ServerLevel level, long chunk);

  void portalix$invalidate(ServerLevel level, long chunk);
}
