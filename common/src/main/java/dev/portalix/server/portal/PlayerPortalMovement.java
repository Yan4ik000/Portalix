package dev.portalix.server.portal;

import dev.portalix.compat.minecraft.ServerPlayerMotion;
import dev.portalix.network.ModNetworking;
import dev.portalix.network.portal.PlayerPortalMoveAckPayload;
import dev.portalix.network.portal.PlayerPortalMovePayload;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.traversal.PortalEntityMovement;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.traversal.PortalPassagePose;
import java.util.Set;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PlayerPortalMovement {
  private PlayerPortalMovement() {}

  public static void receive(ServerPlayer player, PlayerPortalMovePayload input) {
    if (ServerPlayerMotion.awaitingTeleport(player)) return;
    var commandLevel = player.level();
    var state = ((PlayerPortalMoveState.Access) player).portalix$transit();
    if (state.level != player.level()) {
      state.resetForWorld();
      state.level = player.level();
    }
    if (input.sequence() <= state.sequence) return;
    state.sequence = input.sequence();
    state.ownerEvent = EntityPortalState.of(player).crossingSequence();
    state.receiving = true;
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
          || !PortalPassagePose.validScale(player, input.beforeScale())
          || !PortalPassagePose.validScale(player, input.poseScale())
          || !PortalPassagePose.validScale(player, input.finalScale())
          || subject == player && !validPose(player, input.beforePose())
          || subject == player && !validPose(player, input.finalPose())
          || !finite(input.before())
          || !finite(input.movement())
          || !finite(input.velocity())
          || !validAngles(input.beforeYaw(), input.beforePitch())
          || !validAngles(input.playerYaw(), input.playerPitch())
          || subject instanceof LivingEntity
              && input.mover() == MoverType.SELF
              && input.velocity().distanceToSqr(input.movement()) > 1e-12
          || !finite(input.predicted())) {
        acknowledge(player, input.sequence(), false);
        return;
      }
      int tick = player.level().getServer().getTickCount();
      if (state.tick != tick) {
        state.tick = tick;
        state.travelled = 0;
        state.impulseSquared = subject.getDeltaMovement().lengthSqr();
      }
      double distance = subject.position().distanceTo(input.before()) + input.movement().length();
      double budget = (player.isFallFlying() ? 300 : 100) + state.impulseSquared;
      var predictedHull =
          subject
              .getSelfAndPassengers()
              .map(
                  member ->
                      member == player
                          ? PortalPassagePose.hull(
                              player, input.beforePose(), input.beforeScale(), player.position())
                          : member.getBoundingBox())
              .reduce(AABB::minmax)
              .orElseThrow()
              .move(input.before().subtract(subject.position()));
      if (Math.pow(state.travelled + distance, 2) > budget
          || input.velocity().lengthSqr() > budget
          || !PortalPassagePose.compact(player)
              && PortalTopology.links(player.level()).stream()
                  .noneMatch(
                      link ->
                          PortalEntityMovement.canTraverse(player, link)
                              && PortalAperture.touches(
                                  link.entrance().endpoint(),
                                  predictedHull
                                      .expandTowards(input.movement())
                                      .inflate(PortalAperture.RIM_THICKNESS)))) {
        acknowledge(player, input.sequence(), false);
        return;
      }
      state.travelled += distance;
      subject.setYRot(input.beforeYaw());
      subject.setXRot(input.beforePitch());
      player.setYRot(input.playerYaw());
      player.setXRot(input.playerPitch());
      player.setYHeadRot(input.playerYaw());
      subject.setShiftKeyDown(input.sneaking());
      Vec3 initial = subject.position();
      Vec3 correction = input.before().subtract(initial);
      if (correction.lengthSqr() != 0) {
        PortalEntityMovement.moveWithinSpace(subject, MoverType.PLAYER, correction);
        if (player.level() != commandLevel) return;
        if (subject.position().distanceToSqr(input.before()) > 1e-10) {
          ServerPlayerMotion.rebase(subject);
          acknowledge(player, input.sequence(), false);
          return;
        }
      }
      if (subject == player) {
        if (input.beforePose() != player.getPose()
            || Float.compare(input.beforeScale(), PortalPassagePose.scale(player)) != 0) {
          var hull =
              PortalPassagePose.hull(
                  player, input.beforePose(), input.beforeScale(), player.position());
          if (!PortalPassagePose.fits(player, hull)) {
            ServerPlayerMotion.rebase(subject);
            acknowledge(player, input.sequence(), false);
            return;
          }
        }
        player.setPose(input.beforePose());
        PortalPassagePose.set(player, input.beforeScale());
      }
      subject.setOnGround(input.grounded());
      var result =
          PortalEntityMovement.moveAuthoritatively(
              subject, input.mover(), input.movement(), input.velocity());
      if (player.level() != commandLevel) return;
      boolean accepted =
          result != null
              && subject.position().distanceToSqr(input.predicted()) <= 1e-8
              && Float.compare(PortalPassagePose.scale(player), input.poseScale()) == 0;
      if (accepted && subject == player)
        accepted = finishPose(player, input.finalPose(), input.finalScale());
      ServerPlayerMotion.rebase(subject);
      if (result != null && !result.crossings().isEmpty()) player.connection.resetFlyingTicks();
      acknowledge(player, input.sequence(), accepted);
    } finally {
      state.receiving = false;
    }
  }

  private static boolean finishPose(ServerPlayer player, Pose pose, float scale) {
    float currentScale = PortalPassagePose.scale(player);
    if (pose == player.getPose() && Float.compare(scale, currentScale) == 0) return true;
    Vec3 feet = player.position();
    var resized = PortalPassagePose.hull(player, pose, scale, feet);
    if (!PortalPassagePose.fits(player, resized)) return false;
    player.setPose(pose);
    PortalPassagePose.set(player, scale);
    player.setPos(feet);
    return true;
  }

  private static boolean finite(Vec3 point) {
    return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
  }

  private static boolean validPose(Player player, Pose pose) {
    return switch (pose) {
      case STANDING, CROUCHING, SWIMMING -> true;
      case FALL_FLYING -> player.isFallFlying();
      case SLEEPING -> player.isSleeping();
      case SPIN_ATTACK -> player.isAutoSpinAttack();
      default -> false;
    };
  }

  private static boolean validAngles(float yaw, float pitch) {
    return Float.isFinite(yaw) && Float.isFinite(pitch) && Math.abs(pitch) <= 90;
  }

  private static void acknowledge(ServerPlayer player, long sequence, boolean accepted) {
    var state = ((PlayerPortalMoveState.Access) player).portalix$transit();
    ModNetworking.sendToPlayer(
        player,
        new PlayerPortalMoveAckPayload(sequence, accepted, PortalPassagePose.scale(player), state.activeCrossings(player)));
    if (!accepted) {
      var vehicle = player.getRootVehicle();
      if (vehicle != player && vehicle.getControllingPassenger() == player)
        player.connection.send(ClientboundMoveVehiclePacket.fromEntity(vehicle));
      player.connection.teleport(
          new PositionMoveRotation(
              player.position(), player.getDeltaMovement(), player.getYRot(), player.getXRot()),
          Set.of());
    }
    state.completedSequence = sequence;
  }
}
