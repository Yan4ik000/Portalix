package dev.portalix.mixin.world.tracking;

import dev.portalix.world.chunk.ChunkDeliveryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
abstract class PortalPlayerRemovalMixin {
  @Inject(method = "remove", at = @At("TAIL"), require = 1, allow = 1)
  private void portalix$endRemainingWatches(ServerPlayer player, CallbackInfo ci) {
    ((ChunkDeliveryAccess) player.connection.chunkSender).portalix$leaveLevel(player);
  }
}
