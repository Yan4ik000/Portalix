package dev.portalix.platform;

import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;

public interface PortalPlatform {
  PortalPlatform INSTANCE =
      ServiceLoader.load(PortalPlatform.class, PortalPlatform.class.getClassLoader())
          .findFirst()
          .orElseThrow();

  boolean canPlayerReceive(ServerPlayer player, CustomPacketPayload.Type<?> type);

  Collection<ServerPlayer> trackingPlayers(Entity entity);

  boolean beforeBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state);

  default void afterBlockBreak(
      ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {}

  default void publishBlockDrops(
      ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, List<ItemStack> drops) {
    drops.forEach(stack -> Block.popResource(level, pos, stack));
    state.spawnAfterBreak(level, pos, ItemStack.EMPTY, true);
  }

  void syncPortalData(Entity entity);
}
