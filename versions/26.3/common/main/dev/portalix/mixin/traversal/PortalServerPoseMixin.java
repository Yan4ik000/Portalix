package dev.portalix.mixin.traversal;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import dev.portalix.compat.minecraft.ServerPlayerMotion;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.traversal.PortalPassagePose;
import dev.portalix.server.PortalServerEvents;
import dev.portalix.server.portal.PlayerPortalMoveState;
import java.util.Set;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Util;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class PortalServerPoseMixin {
  @Shadow public ServerPlayer player;
  @Unique private boolean portalix$positionCorrection;

  @Inject(
      method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V",
      at = @At("HEAD"), require = 1, allow = 1)
  private void portalix$rememberPositionCorrection(CallbackInfo ci,
      @Share("replacingCorrection") LocalBooleanRef replacingCorrection) {
    replacingCorrection.set(portalix$positionCorrection && ServerPlayerMotion.awaitingTeleport(player));
    portalix$positionCorrection = ServerPlayerMotion.synchronizingPosition(player);
  }

  @Inject(
      method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V",
      at = @At("TAIL"), require = 1, allow = 1)
  private void portalix$resetCameraObservation(CallbackInfo ci) {
    if (!portalix$positionCorrection) PortalServerEvents.resetCamera(player);
  }

  @Inject(method = "updateAwaitingTeleport", at = @At("HEAD"), cancellable = true)
  private void portalix$awaitPositionCorrection(CallbackInfoReturnable<Boolean> ci) {
    // TCP delivers the bundle; old queued movement must not invalidate its pending acknowledgement.
    if (portalix$positionCorrection && ServerPlayerMotion.awaitingTeleport(player)) ci.setReturnValue(true);
  }

  @Inject(method = "handleAcceptTeleportPacket", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;handlePlayerPositionChange(DDDFFZZ)V"),
      require = 1, allow = 1)
  private void portalix$rebaseAfterTeleport(CallbackInfo ci) {
    // The acknowledgement supplies movement from the position the server just installed.
    ServerPlayerMotion.rebaseMovementBudget(player, player);
  }

  @WrapOperation(
      method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V",
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/server/level/ServerPlayer;teleportSetPosition(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V"),
      require = 1, allow = 1)
  private void portalix$preserveAuthoritativePosition(ServerPlayer player,
      PositionMoveRotation destination, Set<Relative> relatives, Operation<Void> original) {
    // A correction sends the current server pose. Teleporting it again clears pending block effects.
    if (!ServerPlayerMotion.synchronizingPosition(player)) original.call(player, destination, relatives);
  }

  @WrapOperation(
      method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V",
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"),
      require = 1, allow = 1)
  private void portalix$atomicPositionUpdates(
      ServerGamePacketListenerImpl listener, Packet<? super ClientGamePacketListener> packet,
      Operation<Void> original, @Share("replacingCorrection") LocalBooleanRef replacingCorrection) {
    var outgoing = ServerPlayerMotion.teleportPacket(player, packet, replacingCorrection.get());
    if (outgoing != null) original.call(listener, outgoing);
  }

  @Inject(method = "tick", at = @At("TAIL"))
  private void portalix$synchronizeMovementClock(CallbackInfo ci) {
    if (!player.connection.hasClientLoaded()) return;
    var subject = player.getRootVehicle();
    if (subject != player && subject.getControllingPassenger() != player) return;
    var replay = PlayerPortalMoveState.of(player);
    if (!replay.continuesMovement(subject)
        && !PortalPassagePose.compact(subject) && !PlayerPortalVisibility.of(player).hasReadyLinks()) return;
    var probe = replay.clockProbe(Util.getMillis());
    if (probe != null) player.connection.send(probe);
  }

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
    PlayerPortalMoveState.of(player).nativeBaselineRefreshed();
    ServerPlayerMotion.updatePose(player);
    EntityPortalState.of(player).cameraHistory().discardThrough(player.tickCount - 1);
  }

  @Inject(method = "handleClientTickEnd", at = @At("TAIL"))
  private void portalix$endClientTick(CallbackInfo ci) {
    var replay = PlayerPortalMoveState.of(player);
    if (replay.replayedMovement(player) || PortalPassagePose.compact(player)) {
      ServerPlayerMotion.afterNativeMovement(player);
      ServerPlayerMotion.advanceFluidState(player);
    }
    replay.endClientTick(Util.getMillis());
    PortalServerEvents.clientTickEnded(player);
  }
}
