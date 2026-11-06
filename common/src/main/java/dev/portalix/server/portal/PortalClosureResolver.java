package dev.portalix.server.portal;

import dev.portalix.portal.PortalLink;
import dev.portalix.portal.geometry.PortalPenetrationResolver;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.traversal.PortalEntityCollisions;
import dev.portalix.portal.traversal.PortalPassengerGroup;
import dev.portalix.portal.traversal.WorldCollisionShapes;
import dev.portalix.resource.SourceUnits;
import dev.portalix.server.EntityObservers;
import dev.portalix.server.PortalServerEvents;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PortalClosureResolver {
  private PortalClosureResolver() {}

  public static List<Release> prepare(ServerLevel level, List<PortalLink> retired) {
    if (retired.isEmpty()) return List.of();
    var releases = new ArrayList<Release>();
    var candidates = new LinkedHashSet<Entity>();
    for (var link : retired) {
      var portal = link.entrance().endpoint();
      double extent = portal.width() + portal.height();
      var bounds = AABB.ofSize(portal.center(), extent, extent, extent).inflate(1e-5);
      for (var entity : level.getEntities((Entity) null, bounds))
        candidates.add(entity.getRootVehicle());
    }
    for (Entity entity : candidates) {
      if (entity.isPassenger() || !entity.isAlive()) continue;
      PortalLink link = PortalEntityCollisions.intersectingPortal(entity);
      if (link != null && retired.contains(link))
        releases.add(new Release(level, entity, link.entrance().endpoint().normal()));
    }
    PortalServerEvents.portalsClosing(level, retired);
    return releases;
  }

  public record Release(ServerLevel level, Entity entity, Vec3 direction) {
    public void resolve() {
      var displacement =
          PortalPenetrationResolver.displacement(
              PortalPassengerGroup.hulls(entity),
              direction,
              query -> WorldCollisionShapes.boxes(entity, query));
      if (displacement.isEmpty()) {
        entity
            .getSelfAndPassengers()
            .filter(ServerPlayer.class::isInstance)
            .forEach(
                member -> {
                  member.hurtServer(level, member.damageSources().inWall(), 1000);
                  member.setDeltaMovement(0, -SourceUnits.SOURCE_SPEED_BLOCKS_PER_TICK, 0);
                });
        return;
      }
      Vec3 shift = displacement.orElseThrow();
      if (shift.lengthSqr() == 0) return;
      entity.teleport(
          new TeleportTransition(
              level,
              entity.position().add(shift),
              Vec3.ZERO,
              entity.getYRot(),
              entity.getXRot(),
              Set.of(Relative.DELTA_X, Relative.DELTA_Y, Relative.DELTA_Z),
              member -> {
                EntityPortalState.of(member).resetCamera();
                var packet =
                    ClientboundTeleportEntityPacket.teleport(
                        member.getId(),
                        PositionMoveRotation.of(member),
                        Set.of(),
                        member.onGround());
                for (var observer : EntityObservers.tracking(member)) observer.connection.send(packet);
              }));
    }
  }
}
