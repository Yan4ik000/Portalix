package dev.portalix.mixin.interaction;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.portal.query.PlayerPortalVisibility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerLevel.class)
abstract class PortalParticleBroadcastMixin {
  @WrapOperation(
      method =
          "sendParticles(Lnet/minecraft/server/level/ServerPlayer;ZDDDLnet/minecraft/network/protocol/Packet;)Z",
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"),
      allow = 1)
  private boolean portalix$particles(
      BlockPos position, Position source, double range, Operation<Boolean> original,
      @Local(argsOnly = true) ServerPlayer player) {
    if (original.call(position, source, range)) return true;
    for (var eye : PlayerPortalVisibility.of(player).eyes()) {
      if (eye.distanceToSqr(source.x(), source.y(), source.z()) < range * range) return true;
    }
    return false;
  }
}
