package dev.portalix.world.chunk;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public interface ChunkDeliveryAccess {
  void portalix$enterLevel(ServerPlayer player);

  void portalix$leaveLevel(ServerPlayer player);

  boolean portalix$subscribed(ServerLevel level, long chunk);

  boolean portalix$watching(ServerLevel level, long chunk);

  boolean portalix$delivered(ServerLevel level, long chunk);

  LongSet portalix$presentColumns(ServerLevel level);

  boolean portalix$retain(ServerPlayer player, long chunk);

  boolean portalix$received(ServerLevel level, long chunk);

  void portalix$confirmRefresh(ServerPlayer player, int id);

  void portalix$invalidate(ServerPlayer player, long chunk);
}
