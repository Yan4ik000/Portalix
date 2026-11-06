package dev.portalix.neoforge;

import dev.portalix.platform.PortalPlatform;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

public final class NeoForgePortalPlatform implements PortalPlatform {
  @Override
  public boolean canPlayerReceive(ServerPlayer player, CustomPacketPayload.Type<?> type) {
    return player.connection.hasChannel(type);
  }

  @Override
  public Collection<ServerPlayer> trackingPlayers(Entity entity) {
    return ((ServerLevel) entity.level()).getChunkSource().chunkMap.getPlayersWatching(entity);
  }

  @Override
  public boolean beforeBlockBreak(
      ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
    return !NeoForge.EVENT_BUS.post(new BreakBlockEvent(level, pos, state, player)).isCanceled();
  }

  @Override
  public void afterBlockBreak(
      ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {}

  @Override
  public void syncPortalData(Entity entity) {
    NeoForgePortalData.sync(entity);
  }
}
