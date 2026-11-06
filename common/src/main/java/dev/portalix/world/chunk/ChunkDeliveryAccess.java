package dev.portalix.world.chunk;

import net.minecraft.server.level.ServerLevel;

public interface ChunkDeliveryAccess {
  boolean portalix$delivered(ServerLevel level, long chunk);

  boolean portalix$retain(ServerLevel level, long chunk);

  void portalix$invalidate(ServerLevel level, long chunk);
}
