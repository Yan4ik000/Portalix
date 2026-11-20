package dev.portalix.fabric;

import dev.portalix.mixin.traversal.EntityAccessor;
import dev.portalix.platform.PortalPlatform;
import java.util.Collection;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

public final class FabricPortalPlatform implements PortalPlatform {
  @Override
  public boolean canPlayerReceive(ServerPlayer player, CustomPacketPayload.Type<?> type) {
    return ServerPlayNetworking.canSend(player, type);
  }

  @Override
  public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
    ServerPlayNetworking.send(player, payload);
  }

  @Override
  public Collection<ServerPlayer> trackingPlayers(Entity entity) {
    return PlayerLookup.tracking(entity);
  }

  @Override
  public boolean beforeBlockBreak(
      ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
    return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, null);
  }

  @Override
  public void afterBlockBreak(
      ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
    PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, pos, state, null);
  }

  @Override
  public boolean refreshPlayerFluids(ServerPlayer player) {
    var access = (EntityAccessor) player;
    access.portalix$wasEyeInWater(player.isEyeInFluid(FluidTags.WATER));
    var fluids = access.portalix$fluidInteraction();
    fluids.update(player, true);
    return fluids.isInFluid(FluidTags.WATER);
  }
}
