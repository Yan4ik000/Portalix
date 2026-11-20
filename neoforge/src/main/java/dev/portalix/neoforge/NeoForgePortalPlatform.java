package dev.portalix.neoforge;

import dev.portalix.mixin.traversal.EntityAccessor;
import dev.portalix.platform.PortalPlatform;
import dev.portalix.portal.camera.PortalCameraDistance;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NeoForgePortalPlatform implements PortalPlatform {
  private static final ScopedValue<ChunkWatchEvent.Sent> RESUMED_WATCH = ScopedValue.newInstance();

  @Override
  public boolean canPlayerReceive(ServerPlayer player, CustomPacketPayload.Type<?> type) {
    return player.connection.hasChannel(type);
  }

  @Override
  public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
    PacketDistributor.sendToPlayer(player, payload);
  }

  @Override
  public float maximumCameraDistance(ServerPlayer player, int requestedDistance) {
    // NeoForge only fires the camera distance event on the client.
    return Math.max(PortalCameraDistance.limit(player), requestedDistance);
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
  public void publishBlockDrops(
      ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, List<ItemStack> drops) {
    List<ItemEntity> captured;
    Block.beginCapturingDrops();
    try {
      drops.forEach(stack -> Block.popResource(level, pos, stack));
    } finally {
      captured = Block.stopCapturingDrops();
    }
    CommonHooks.handleBlockDrops(level, pos, state, null, captured, player, ItemStack.EMPTY);
  }

  @Override
  public void resumeChunkWatch(ServerPlayer player, LevelChunk chunk) {
    var event = new ChunkWatchEvent.Sent(player, chunk, player.level());
    ScopedValue.where(RESUMED_WATCH, event).run(() -> NeoForge.EVENT_BUS.post(event));
  }

  public static boolean isResumedWatch(ChunkWatchEvent.Sent event) {
    return RESUMED_WATCH.isBound() && RESUMED_WATCH.get() == event;
  }

  @Override
  public void endChunkWatch(ServerPlayer player, ServerLevel level, ChunkPos chunk) {
    EventHooks.fireChunkUnWatch(player, chunk, level);
  }

  @Override
  public void syncPortalData(Entity entity) {
    NeoForgePortalData.sync(entity);
  }

  @Override
  public boolean refreshPlayerFluids(ServerPlayer player) {
    var fluids = player.getFluidInteraction();
    ((EntityAccessor) player).portalix$wasEyeInWater(
        fluids.isEyeInFluidMatching(player, (entity, type, height) -> type.getIsWaterLike()));
    fluids.update(player, type -> false);
    return fluids.isInFluidMatching(player, (entity, type, height) -> type.getIsWaterLike());
  }
}
