package dev.portalix.compat.minecraft;

import dev.portalix.mixin.traversal.PlayerAccessor;
import dev.portalix.mixin.traversal.ServerPlayerAccessor;
import dev.portalix.mixin.world.tracking.ServerGamePacketListenerImplAccessor;
import dev.portalix.portal.traversal.EntityPortalState;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class ServerPlayerMotion {
  private ServerPlayerMotion() {}

  public static List<MovementSample> captureStatistics(Entity entity) {
    return entity.getSelfAndPassengers()
        .filter(ServerPlayer.class::isInstance)
        .map(member -> new MovementSample((ServerPlayer) member, member.position()))
        .toList();
  }

  public static void updateStatistics(List<MovementSample> samples) {
    for (var sample : samples) {
      Vec3 movement = sample.player().position().subtract(sample.before());
      if (sample.player().isPassenger())
        ((ServerPlayerAccessor) sample.player())
            .portalix$ridingStatistics(movement.x, movement.y, movement.z);
      else sample.player().checkMovementStatistics(movement.x, movement.y, movement.z);
    }
  }

  public record MovementSample(ServerPlayer player, Vec3 before) {}

  public static boolean awaitingTeleport(ServerPlayer player) {
    return ((ServerGamePacketListenerImplAccessor) player.connection).portalix$awaitingTeleport() != null;
  }

  public static void afterNativeMovement(ServerPlayer player) {
    ((PlayerAccessor) player).portalix$updatePose();
    EntityPortalState.of(player).cameraHistory().discardThrough(player.tickCount - 1);
  }

  public static void rebase(Entity subject) {
    subject.getSelfAndPassengers()
        .filter(ServerPlayer.class::isInstance)
        .map(ServerPlayer.class::cast)
        .forEach(player -> {
          player.connection.resetPosition();
          player.level().getChunkSource().move(player);
          if (subject != player && subject.getControllingPassenger() == player) {
            var access = (ServerGamePacketListenerImplAccessor) player.connection;
            access.portalix$lastVehicle(subject);
            access.portalix$vehicleFirstX(subject.getX());
            access.portalix$vehicleLastX(subject.getX());
            access.portalix$vehicleFirstY(subject.getY());
            access.portalix$vehicleLastY(subject.getY());
            access.portalix$vehicleFirstZ(subject.getZ());
            access.portalix$vehicleLastZ(subject.getZ());
          }
        });
  }
}
