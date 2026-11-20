package dev.portalix.server;

import dev.portalix.api.PortalColor;
import dev.portalix.api.PortalEndpoint;
import dev.portalix.gun.GunFireTiming;
import dev.portalix.gun.GunMuzzle;
import dev.portalix.item.PortalixIdentity;
import dev.portalix.item.PortalixVariant;
import dev.portalix.network.ModNetworking;
import dev.portalix.network.gun.GunEventKind;
import dev.portalix.network.gun.GunShotPayload;
import dev.portalix.network.gun.PortalImpactPayload;
import dev.portalix.portal.camera.PortalEyePose;
import dev.portalix.portal.placement.BlockPortalPlacement;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.server.portal.GunPortalRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

final class PortalShotManager {
  private final GunPortalRegistry portalRegistry;
  private final GunStateBroadcaster broadcaster;
  private final List<PendingShot> pendingShots = new ArrayList<>();
  private long shotSequence;

  private record PendingShot(
      long targetTick,
      UUID owner,
      UUID gun,
      ServerLevel level,
      PortalColor color,
      float yaw,
      BlockHitResult hit,
      GunShotPayload shot,
      Set<UUID> audience,
      double range) {}

  PortalShotManager(GunPortalRegistry portalRegistry, GunStateBroadcaster broadcaster) {
    this.portalRegistry = portalRegistry;
    this.broadcaster = broadcaster;
  }

  void synchronize(ServerPlayer observer, boolean force) {
    for (var pending : pendingShots) {
      if (observer.level() == pending.level
          && (observes(observer, pending.shot.start(), pending.range * pending.range)
              || observes(observer, pending.shot.end(), pending.range * pending.range))
          && ModNetworking.canPlayerReceive(observer, GunShotPayload.TYPE)) {
        if (pending.audience.add(observer.getUUID()) || force)
          ModNetworking.sendToPlayer(observer, pending.shot);
      }
    }
  }

  private static boolean observes(ServerPlayer observer, Vec3 position, double squaredRange) {
    if (observer.distanceToSqr(position) <= squaredRange) return true;
    return PlayerPortalVisibility.of(observer).eyes().stream()
        .anyMatch(eye -> eye.distanceToSqr(position) <= squaredRange);
  }

  void fire(ServerPlayer player, boolean orange) {
    MinecraftServer server = player.level().getServer();
    int viewDistance =
        player.requestedViewDistance() > 0
            ? player.requestedViewDistance()
            : server.getPlayerList().getViewDistance();
    double maxRange = viewDistance * 16.0 * 0.9;

    ServerLevel level = player.level();
    var pose = PortalEyePose.sample(player);
    Vec3 eye = pose.position();
    Vec3 look = pose.forward();
    Vec3 end = eye.add(look.scale(maxRange));
    BlockHitResult hit = BlockPortalPlacement.trace(player, eye, end);
    Vec3 muzzle =
        GunMuzzle.position(eye, look, player.getYRot(), player.getMainArm() == HumanoidArm.LEFT);

    boolean miss = hit.getType() == Type.MISS;
    PortalColor color = orange ? PortalColor.ORANGE : PortalColor.BLUE;

    Vec3 impactPos = hit.getLocation();
    int travelTicks = GunFireTiming.shotTravelTicks(muzzle.distanceTo(impactPos));

    long sequence = ++this.shotSequence;

    broadcaster.sendEvent(player, orange ? GunEventKind.FIRE_ORANGE : GunEventKind.FIRE_BLUE);

    Vec3 normal = miss ? Vec3.ZERO : Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i());

    GunShotPayload shot =
        new GunShotPayload(
            player.getId(),
            muzzle,
            impactPos,
            normal,
            orange,
            look,
            level.getGameTime(),
            travelTicks,
            sequence,
            PortalixVariant.of(player.getMainHandItem()));

    double trackingRangeSqr = maxRange * maxRange;
    Set<UUID> audience = new HashSet<>();
    for (ServerPlayer other : level.players()) {
      if ((observes(other, muzzle, trackingRangeSqr)
              || observes(other, impactPos, trackingRangeSqr))
          && ModNetworking.canPlayerReceive(other, GunShotPayload.TYPE)) {
        ModNetworking.sendToPlayer(other, shot);
        audience.add(other.getUUID());
      }
    }
    this.pendingShots.add(
        new PendingShot(
            server.getTickCount() + travelTicks,
            player.getUUID(),
            PortalixIdentity.ensure(player.getMainHandItem()),
            level,
            color,
            player.getYRot(),
            hit,
            shot,
            audience,
            maxRange));
  }

  void tick(MinecraftServer server) {
    long now = server.getTickCount();
    var iterator = pendingShots.iterator();
    while (iterator.hasNext()) {
      PendingShot pending = iterator.next();
      ServerPlayer player = server.getPlayerList().getPlayer(pending.owner);
      if (player == null
          || !player.isAlive()
          || player.isSpectator()
          || player.level() != pending.level) {
        iterator.remove();
        continue;
      }
      if (now < pending.targetTick) continue;
      iterator.remove();
      if (pending.hit.getType() == Type.MISS) continue;
      impact(player, pending);
    }
  }

  private void impact(ServerPlayer player, PendingShot pending) {
    var placement =
        BlockPortalPlacement.find(
            player,
            pending.hit,
            pending.yaw,
            portalRegistry.obstacles(
                pending.gun, pending.color, pending.level.dimension(), pending.hit.getLocation()));
    boolean placed =
        placement.isPresent()
            && portalRegistry.apply(
                player, pending.gun, pending.color, placement.get(), pending.shot.variant());
    PortalImpactPayload impact =
        new PortalImpactPayload(
            pending.shot.sequence(),
            pending.shot.start(),
            placement.map(PortalEndpoint::center).orElse(pending.hit.getLocation()),
            placement.map(PortalEndpoint::normal).orElse(pending.shot.normal()),
            pending.color == PortalColor.ORANGE,
            placed,
            pending.level.getGameTime(),
            pending.shot.variant());
    for (ServerPlayer observer : pending.level.players()) {
      if ((pending.audience.contains(observer.getUUID())
              || observes(observer, impact.position(), pending.range * pending.range))
          && ModNetworking.canPlayerReceive(observer, PortalImpactPayload.TYPE)) {
        ModNetworking.sendToPlayer(observer, impact);
      }
    }
  }

  boolean cancel(UUID gun) {
    return pendingShots.removeIf(shot -> shot.gun.equals(gun));
  }

  void disconnect(UUID player) {
    pendingShots.removeIf(shot -> shot.owner.equals(player));
    for (var shot : pendingShots) shot.audience.remove(player);
  }

  void clear() {
    pendingShots.clear();
  }
}
