package dev.portalix.server.portal;

import dev.portalix.compat.minecraft.ServerPlayerMotion;
import dev.portalix.network.ModNetworking;
import dev.portalix.network.portal.PlayerPortalMoveAckPayload;
import dev.portalix.network.portal.PlayerPortalMovePayload;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.portal.traversal.PortalEntityMovement;
import dev.portalix.portal.traversal.PortalPassagePose;
import java.util.List;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class PlayerPortalMovement {
  private PlayerPortalMovement() {}

  public static void receive(ServerPlayer player, PlayerPortalMovePayload input) {
    if (ServerPlayerMotion.awaitingTeleport(player)) return;
    var commandLevel = player.level();
    var state = PlayerPortalMoveState.of(player);
    if (!state.beginReplay(player, input.sequence())) return;
    try {
      Entity subject = player.level().getEntity(input.entityId());
      boolean controlled =
          subject != null
              && subject.getUUID().equals(input.entityUuid())
              && (subject == player && !player.isPassenger()
                  || subject == player.getRootVehicle()
                      && subject.getControllingPassenger() == player);
      if (!controlled
          || !player.connection.hasClientLoaded()
          || !player.isAlive()
          || subject.noPhysics
          || !PortalEntityMovement.clientPredicted(subject, input.mover(), input.movement(), input.velocity())
          || !validScale(input.poseScale())
          || input.finishesTick() && !validScale(input.finalScale())
          || !input.before().isFinite()
          || !input.movement().isFinite()
          || !input.velocity().isFinite()
          || !validAngles(input.beforeYaw(), input.beforePitch())
          || !validAngles(input.playerYaw(), input.playerPitch())
          || !input.predicted().isFinite()) {
        acknowledge(player, state, input.sequence(), false);
        return;
      }
      Vec3 alignment = input.before().subtract(subject.position());
      double distance = alignment.length() + input.movement().length();
      Vec3 impulse = subject.getDeltaMovement();
      double budget = state.movementBudget(impulse.lengthSqr(), player.isFallFlying());
      if (!state.withinMovementBudget(distance, budget)
          || input.velocity().lengthSqr() > budget
          || !PortalEntityMovement.canReplay(subject, alignment, input.movement())
          || !state.beginMovement(Util.getMillis(), impulse)) {
        acknowledge(player, state, input.sequence(), false);
        return;
      }
      Vec3 start = subject.position();
      boolean aligned = PortalEntityMovement.alignReplay(subject, alignment);
      if (player.level() != commandLevel) return;
      if (!aligned) {
        state.recordReplayDistance(start.distanceTo(subject.position()));
        acknowledge(player, state, input.sequence(), false);
        return;
      }
      state.recordReplayDistance(distance);
      subject.setYRot(input.beforeYaw());
      subject.setXRot(input.beforePitch());
      player.setYRot(input.playerYaw());
      player.setXRot(input.playerPitch());
      player.setYHeadRot(input.playerYaw());
      subject.setShiftKeyDown(input.sneaking());
      var result =
          PortalEntityMovement.moveAuthoritatively(
              subject, input.mover(), input.movement(), input.velocity());
      if (player.level() != commandLevel) return;
      boolean accepted =
          result != null
              && subject.position().distanceToSqr(input.predicted()) <= 1e-8
              && Float.compare(PortalPassagePose.scale(player), input.poseScale()) == 0;
      if (subject == player && input.finishesTick()) {
        ServerPlayerMotion.afterNativeMovement(player);
        accepted &= input.finalPose() == player.getPose()
            && Float.compare(PortalPassagePose.scale(player), input.finalScale()) == 0;
      }
      ServerPlayerMotion.rebase(subject);
      if (result != null && !result.crossings().isEmpty()) player.connection.resetFlyingTicks();
      acknowledge(player, state, input.sequence(), accepted);
    } finally {
      state.endReplay();
    }
  }

  private static boolean validScale(float scale) {
    return Float.isFinite(scale) && scale > 0 && scale <= 1;
  }

  private static boolean validAngles(float yaw, float pitch) {
    return Float.isFinite(yaw) && Float.isFinite(pitch) && Math.abs(pitch) <= 90;
  }

  private static void acknowledge(ServerPlayer player, PlayerPortalMoveState state, long sequence, boolean accepted) {
    var acknowledgement = new PlayerPortalMoveAckPayload(
        sequence, accepted, PortalPassagePose.scale(player),
        PlayerPortalVisibility.of(player).resumeLoadingAt(), state.activeCrossings(player));
    if (accepted) ModNetworking.sendToPlayer(player, acknowledgement);
    else {
      var vehicle = player.getRootVehicle();
      var reply = new ClientboundCustomPayloadPacket(acknowledgement);
      ServerPlayerMotion.synchronizePosition(player, vehicle != player && vehicle.getControllingPassenger() == player
          ? List.of(reply, new ClientboundSetEntityMotionPacket(vehicle)) : List.of(reply));
    }
    state.completeReplay(sequence);
  }
}
