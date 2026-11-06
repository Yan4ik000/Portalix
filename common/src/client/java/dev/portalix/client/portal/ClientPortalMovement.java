package dev.portalix.client.portal;

import dev.portalix.carry.CarryBindings;
import dev.portalix.client.ClientNetworking;
import dev.portalix.client.PortalixClient;
import dev.portalix.client.portal.camera.PortalCameraController;
import dev.portalix.network.portal.EntityPortalCrossingPayload;
import dev.portalix.network.portal.EntityPortalInitPayload;
import dev.portalix.network.portal.PlayerPortalMoveAckPayload;
import dev.portalix.network.portal.PlayerPortalMovePayload;
import dev.portalix.compat.minecraft.EntityMotion;
import dev.portalix.portal.PortalLinkId;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.traversal.ProjectilePortalMovement;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.traversal.PortalEntityInitialization;
import dev.portalix.portal.traversal.PortalCrossingSync;
import dev.portalix.portal.traversal.PortalEntityMovement;
import dev.portalix.portal.traversal.PortalPassagePose;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.projectile.FishingHook;

public final class ClientPortalMovement {
  private static long sequence;
  private static long acknowledged;
  private static final List<PortalEntityMovement.Crossing> pendingCrossings = new ArrayList<>();
  private static final List<PlayerPortalMovePayload> pendingMoves = new ArrayList<>();
  private static final PlayerPortalMoves ownerMoves = new PlayerPortalMoves();

  private ClientPortalMovement() {}

  public static void register(ClientNetworking.Registration registration) {
    ClientProjectileMovement.register(registration);
    registration.register(EntityPortalInitPayload.TYPE, (packet, context) -> {
      var level = context.level;
      var entity = level == null ? null : level.getEntity(packet.entityId());
      if (entity == null || !entity.getUUID().equals(packet.entityUuid())) return;
      List<PortalLink> ownerRoute = List.of();
      if (entity instanceof FishingHook hook && packet.state() instanceof EntityPortalInitPayload.FishingTether tether) {
        var owner = hook.getPlayerOwner();
        if (owner != null && owner == Minecraft.getInstance().player
            && (!owner.isPassenger() || owner.getRootVehicle().getControllingPassenger() == owner))
          ownerRoute = ownerMoves.ownerRoute(tether.completedOwnerMove(), tether.activeOwnerCrossings());
        else if (owner != null) {
          var remote = RemotePortalInterpolation.of(owner);
          if (remote != null) ownerRoute = remote.pendingCrossings().stream()
              .map(PortalEntityMovement.Crossing::link).toList();
        }
      }
      PortalEntityInitialization.apply(entity, packet, ownerRoute);
    });
    PortalCrossingSync.setClientListeners(
        (entity, event) -> {
          if (entity != Minecraft.getInstance().player) return;
          PortalCameraController.bodyCrossed(
              event.eventId(),
              event.link(),
              event.beforeYaw(),
              event.beforePitch());
          var player = Minecraft.getInstance().player;
          if (!player.isPassenger() || player.getRootVehicle().getControllingPassenger() == player)
            pendingCrossings.add(event);
        },
        (entity, result) -> {
          var player = Minecraft.getInstance().player;
          if (player == null
              || entity != player
                  && (entity != player.getRootVehicle()
                      || entity.getControllingPassenger() != player)) return;
          var first = result.crossings().isEmpty() ? null : result.crossings().getFirst();
          var rider = pendingCrossings.isEmpty() ? null : pendingCrossings.getFirst();
          float beforeYaw = first == null ? entity.getYRot() : first.beforeYaw();
          float beforePitch = first == null ? entity.getXRot() : first.beforePitch();
          float playerYaw = rider == null ? player.getYRot() : rider.beforeYaw();
          float playerPitch = rider == null ? player.getXRot() : rider.beforePitch();
          var ownerRoute = pendingCrossings.stream().map(PortalEntityMovement.Crossing::link).toList();
          pendingCrossings.clear();
          var input =
              new PlayerPortalMovePayload(
                  ++sequence,
                  entity.getId(),
                  entity.getUUID(),
                  result.mover(),
                  result.beforeFeet(),
                  result.requested(),
                  result.beforeVelocity(),
                  result.grounded(),
                  result.beforePose(),
                  result.beforeScale(),
                  result.sneaking(),
                  result.afterFeet(),
                  PortalPassagePose.scale(player),
                  beforeYaw,
                  beforePitch,
                  playerYaw,
                  playerPitch,
                  player.getPose(),
                  PortalPassagePose.scale(player));
          ownerMoves.predicted(input.sequence(), ownerRoute);
          if (entity == player) pendingMoves.add(input);
          else {
            flushMovement();
            ClientNetworking.sendToServer(input);
          }
        });
    registration.register(
        PlayerPortalMoveAckPayload.TYPE,
        (packet, context) -> {
          if (packet.sequence() <= acknowledged) return;
          acknowledged = packet.sequence();
          if (context.player == null) return;
          applyOwnerMoves(context.player, ownerMoves.acknowledged(packet.sequence(), packet.accepted(), packet.crossings()));
          if (!packet.accepted()) PortalPassagePose.set(context.player, packet.poseScale());
        });
    registration.register(
        EntityPortalCrossingPayload.TYPE,
        (packet, context) -> receive(packet));
  }

  private static void receive(EntityPortalCrossingPayload packet) {
    var client = Minecraft.getInstance();
    var entity = client.level == null ? null : client.level.getEntity(packet.entityId());
    if (entity == null
        || !entity.getUUID().equals(packet.entityUuid())
        || entity == client.player
            && (!entity.isPassenger()
                || entity.getRootVehicle().getControllingPassenger() == entity)) return;
    var crossings = EntityPortalState.of(entity).acceptAuthoritative(packet.crossings());
    if (crossings.isEmpty()) return;
    var interpolation = entity.getInterpolation();
    var remote = RemotePortalInterpolation.of(entity);
    if (remote != null
        && (!entity.isLocalInstanceAuthoritative()
            || entity == client.player && entity.isPassenger())
        && !PortalixClient.carryController().predicted(entity)) {
      remote.receive(packet, crossings);
      return;
    }
    if (remote != null) remote.finishAccepted();
    if (interpolation != null) interpolation.cancel();
    boolean acknowledged = PortalEntityMovement.acknowledgePredictions(entity, crossings);
    boolean predicted = acknowledged && EntityPortalState.of(entity).hasPredictions();
    for (var crossing : crossings) {
      if (!predicted) EntityMotion.receiveCrossing(entity, crossing);
      if (!acknowledged) PortalEntityMovement.reconcileCrossing(entity, crossing, crossing.previousFeet());
      PortalixClient.carryController().crossed(entity, crossing.link());
    }
    if (predicted) return;
    PortalPassagePose.set(entity, packet.poseScale());
    entity.setPos(packet.position());
    entity.setDeltaMovement(packet.velocity());
    EntityMotion.receivedRotation(entity, packet.yaw(), packet.pitch(), packet.headYaw(), packet.bodyYaw());
    entity.resetFallDistance();
    if (entity instanceof AbstractMinecart minecart
        && minecart.getBehavior() instanceof RemotePortalInterpolation.MinecartAccess access)
      access.portalix$resetInterpolation();
  }

  private static void applyOwnerMoves(Player player, List<PortalLink> crossings) {
    for (var crossing : crossings) {
      CarryBindings.ownerCrossed(player, PortalLinkId.fromLink(crossing));
      ProjectilePortalMovement.ownerCrossed(player, crossing);
    }
  }

  public static void corrected(Entity entity, Set<Relative> relatives) {
    if (entity != Minecraft.getInstance().player
        || relatives.contains(Relative.X) && relatives.contains(Relative.Y) && relatives.contains(Relative.Z)) return;
    applyOwnerMoves((Player) entity, ownerMoves.corrected());
    pendingCrossings.clear();
    pendingMoves.clear();
  }

  public static void clear() {
    ownerMoves.clear();
    pendingMoves.clear();
    pendingCrossings.clear();
    sequence = 0;
    acknowledged = 0;
  }

  public static void flushMovement() {
    var player = Minecraft.getInstance().player;
    if (player == null || pendingMoves.isEmpty()) return;
    for (int i = 0; i < pendingMoves.size(); i++) {
      var input = pendingMoves.get(i);
      if (i == pendingMoves.size() - 1)
        input = input.withFinalPose(player.getPose(), PortalPassagePose.scale(player));
      ClientNetworking.sendToServer(input);
    }
    pendingMoves.clear();
  }
}
