package dev.portalix.platform;

import dev.portalix.portal.camera.PortalCameraDistance;
import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

public interface PortalPlatform {
  PortalPlatform INSTANCE =
      ServiceLoader.load(PortalPlatform.class, PortalPlatform.class.getClassLoader())
          .findFirst()
          .orElseThrow();

  boolean canPlayerReceive(ServerPlayer player, CustomPacketPayload.Type<?> type);

  void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

  default float maximumCameraDistance(ServerPlayer player, int requestedDistance) {
    return PortalCameraDistance.vanilla(player);
  }

  Collection<ServerPlayer> trackingPlayers(Entity entity);

  boolean beforeBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state);

  default void afterBlockBreak(
      ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {}

  default void publishBlockDrops(
      ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, List<ItemStack> drops) {
    drops.forEach(stack -> Block.popResource(level, pos, stack));
    state.spawnAfterBreak(level, pos, ItemStack.EMPTY, true);
  }

  default void resumeChunkWatch(ServerPlayer player, LevelChunk chunk) {}

  default void endChunkWatch(ServerPlayer player, ServerLevel level, ChunkPos chunk) {}

  default void syncPortalData(Entity entity) {}

  boolean refreshPlayerFluids(ServerPlayer player);
}
