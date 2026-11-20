package dev.portalix.compat.minecraft;

import dev.portalix.mixin.traversal.EntityAccessor;
import dev.portalix.mixin.traversal.PlayerAccessor;
import dev.portalix.mixin.traversal.ServerPlayerAccessor;
import dev.portalix.mixin.world.tracking.ServerGamePacketListenerImplAccessor;
import dev.portalix.platform.PortalPlatform;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.traversal.PortalPassagePose;
import dev.portalix.server.portal.PlayerPortalMoveState;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class ServerPlayerMotion {
  private static final ScopedValue<PositionUpdates> POSITION_UPDATES = ScopedValue.newInstance();
  private static final ScopedValue<MountPosition> MOUNT_POSITION = ScopedValue.newInstance();
  private static final ScopedValue<ObservationMovement> OBSERVATION_MOVEMENT = ScopedValue.newInstance();

  private ServerPlayerMotion() {}

  public static void synchronizePosition(
      ServerPlayer player, List<Packet<? super ClientGamePacketListener>> packets) {
    var vehicle = player.getRootVehicle();
    var vehiclePacket = vehicle != player && vehicle.getControllingPassenger() == player
        ? ClientboundMoveVehiclePacket.fromEntity(vehicle) : null;
    var updates = new PositionUpdates(player, List.copyOf(packets), vehiclePacket);
    ScopedValue.where(POSITION_UPDATES, updates).run(() ->
        player.connection.teleport(new PositionMoveRotation(player.position(), player.getDeltaMovement(),
            player.getYRot(), player.getXRot()), Set.of()));
  }

  public static @Nullable Packet<? super ClientGamePacketListener> captureMountPosition(
      ServerPlayer player, Runnable teleport) {
    var capture = new MountPosition(player);
    ScopedValue.where(MOUNT_POSITION, capture).run(teleport);
    return capture.packet;
  }

  public static @Nullable Packet<? super ClientGamePacketListener> teleportPacket(
      ServerPlayer player, Packet<? super ClientGamePacketListener> packet, boolean replacingCorrection) {
    if (MOUNT_POSITION.isBound() && MOUNT_POSITION.get().player == player) {
      MOUNT_POSITION.get().packet = packet;
      return null;
    }
    if (!synchronizingPosition(player)) {
      // A native relative teleport is calculated in the server's space, which may differ
      // from the client's space until an outstanding portal movement is acknowledged.
      if (replacingCorrection && packet instanceof ClientboundPlayerPositionPacket teleport
          && !teleport.relatives().isEmpty())
        return ClientboundPlayerPositionPacket.of(teleport.id(), new PositionMoveRotation(player.position(),
            player.getDeltaMovement(), player.getYRot(), player.getXRot()), Set.of());
      return packet;
    }
    var updates = POSITION_UPDATES.get();
    var packets = new ArrayList<>(updates.prefix());
    packets.add(packet);
    if (updates.vehicle() != null) packets.add(updates.vehicle());
    return new ClientboundBundlePacket(packets);
  }

  public static boolean synchronizingPosition(ServerPlayer player) {
    return POSITION_UPDATES.isBound() && POSITION_UPDATES.get().player() == player;
  }

  private record PositionUpdates(
      ServerPlayer player, List<Packet<? super ClientGamePacketListener>> prefix,
      @Nullable ClientboundMoveVehiclePacket vehicle) {}

  private static final class MountPosition {
    private final ServerPlayer player;
    private @Nullable Packet<? super ClientGamePacketListener> packet;

    private MountPosition(ServerPlayer player) {
      this.player = player;
    }
  }

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

  public static void withAcceptedMovement(ServerPlayer player, Entity subject,
      double beforeX, double beforeY, double beforeZ, Runnable updateTracking) {
    if (PortalTopology.links(player.level()).isEmpty()) {
      updateTracking.run();
      return;
    }
    // Native movement updates the chunk map before publishing this packet's known movement.
    var movement = subject.position().subtract(beforeX, beforeY, beforeZ);
    var observation = new ObservationMovement(
        player, PlayerPortalMoveState.of(player).movementWithReplay(subject, movement));
    ScopedValue.where(OBSERVATION_MOVEMENT, observation).run(updateTracking);
  }

  public static Vec3 observationMovement(ServerPlayer player) {
    if (OBSERVATION_MOVEMENT.isBound()) {
      var observation = OBSERVATION_MOVEMENT.get();
      if (observation.player() == player) return observation.movement();
    }
    return player.getKnownMovement();
  }

  private record ObservationMovement(ServerPlayer player, Vec3 movement) {}

  public static boolean awaitingTeleport(ServerPlayer player) {
    return ((ServerGamePacketListenerImplAccessor) player.connection).portalix$awaitingTeleport() != null;
  }

  public static void rebaseMovementBudget(ServerPlayer player, Entity subject) {
    var access = (ServerGamePacketListenerImplAccessor) player.connection;
    if (subject == player) {
      access.portalix$playerFirstX(subject.getX());
      access.portalix$playerFirstY(subject.getY());
      access.portalix$playerFirstZ(subject.getZ());
    } else {
      access.portalix$vehicleFirstX(subject.getX());
      access.portalix$vehicleFirstY(subject.getY());
      access.portalix$vehicleFirstZ(subject.getZ());
    }
  }

  public static void updatePose(ServerPlayer player) {
    ((PlayerAccessor) player).portalix$updatePose();
  }

  public static void afterNativeMovement(ServerPlayer player) {
    updatePose(player);
    PortalPassagePose.tick(player);
  }

  public static void advanceFluidState(ServerPlayer player) {
    // Advance native immersion bookkeeping for the next client tick; baseTick owns fluid forces.
    var access = (EntityAccessor) player;
    boolean inWater = PortalPlatform.INSTANCE.refreshPlayerFluids(player);
    if (inWater) {
      player.resetFallDistance();
      if (!player.isInWater() && !access.portalix$firstTick()) access.portalix$waterSplash();
    }
    access.portalix$wasTouchingWater(inWater);
    player.updateSwimming();
  }

  public static void replayedSegment(Entity subject, Vec3 movement) {
    var driver = subject instanceof ServerPlayer player ? player : subject.getControllingPassenger();
    if (!(driver instanceof ServerPlayer player)) return;
    if (!subject.isLocalInstanceAuthoritative())
      subject.doCheckFallDamage(movement.x, movement.y, movement.z, subject.onGround());
    var replay = PlayerPortalMoveState.of(player);
    if (movement.lengthSqr() != 0) {
      replay.enterLevel(player.level());
      replay.continueMovement(subject, Util.getMillis());
    }
    ((ServerGamePacketListenerImplAccessor) player.connection)
        .portalix$recordKnownMovement(replay.recordReplay(subject, movement));
    if (subject == player) {
      if (movement.y > 0) player.resetFallDistance();
      if (player.onGround() || player.hasLandedInLiquid() || player.onClimbable()
          || player.isSpectator() || player.isFallFlying() || player.isAutoSpinAttack())
        player.tryResetCurrentImpulseContext();
    }
  }

  public static void crossed(Entity subject, PortalLink link) {
    var driver = subject instanceof ServerPlayer player ? player : subject.getControllingPassenger();
    if (!(driver instanceof ServerPlayer player)) return;
    var replay = PlayerPortalMoveState.of(player);
    Vec3 movement = replay.crossedReplay(subject, link);
    if (movement != null)
      ((ServerGamePacketListenerImplAccessor) player.connection).portalix$recordKnownMovement(movement);
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
            rebaseMovementBudget(player, subject);
            access.portalix$vehicleLastX(subject.getX());
            access.portalix$vehicleLastY(subject.getY());
            access.portalix$vehicleLastZ(subject.getZ());
          }
        });
  }
}
