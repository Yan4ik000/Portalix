package dev.portalix.server;

import dev.portalix.platform.PortalPlatform;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

public final class EntityObservers {
  private EntityObservers() {}

  public static Collection<ServerPlayer> tracking(Entity entity) {
    return PortalPlatform.INSTANCE.trackingPlayers(entity);
  }

  public static Collection<ServerPlayer> tracking(ServerLevel level, BlockPos pos) {
    return level.getChunkSource().chunkMap.getPlayers(ChunkPos.containing(pos), false);
  }
}
