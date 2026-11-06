package dev.portalix.mixin.traversal;

import dev.portalix.compat.minecraft.ServerPlayerMotion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class PortalServerPoseMixin {
  @Shadow public ServerPlayer player;

  @Inject(
      method = "tickPlayer",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/server/level/ServerPlayer;absSnapTo(DDDFF)V",
              shift = At.Shift.AFTER),
      require = 1,
      allow = 1)
  private void portalix$poseAfterMovement(CallbackInfoReturnable<Boolean> ci) {
    ServerPlayerMotion.afterNativeMovement(player);
  }
}
