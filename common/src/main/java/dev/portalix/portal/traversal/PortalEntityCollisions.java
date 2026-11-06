package dev.portalix.portal.traversal;

import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.geometry.ConvexPolyhedron;
import dev.portalix.portal.geometry.PortalAperture;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PortalEntityCollisions {
  private PortalEntityCollisions() {}

  public static Vec3 collide(Entity entity, Vec3 displacement) {
    var state = EntityPortalState.of(entity);
    var link = state.collisionLink;
    if (link == null) link = movementPortal(entity, displacement);
    if (link == null) return null;
    var hulls = PortalPassengerGroup.hulls(entity);
    AABB box = PortalPassengerGroup.bounds(hulls);
    AABB query = box.expandTowards(displacement).expandTowards(0, entity.maxUpStep(), 0);
    var scene =
        state.collisionScene != null
            ? state.collisionScene
            : PortalCollisionWorld.query(entity, link, query);
    return scene.collide(hulls, displacement, entity.maxUpStep(), entity.onGround());
  }

  static PortalLink movementPortal(Entity entity, Vec3 movement) {
    return intersectingPortal(
        entity, PortalPassengerGroup.bounds(entity), movement, entity.getBoundingBox().getCenter());
  }

  public static PortalLink intersectingPortal(Entity entity, AABB hull, Vec3 movement) {
    return intersectingPortal(entity, hull, movement, hull.getCenter());
  }

  static PortalLink intersectingPortal(Entity entity, AABB hull, Vec3 movement, Vec3 anchor) {
    var last = EntityPortalState.of(entity).lastCrossing();
    var sweep = hull.expandTowards(movement);
    return PortalAperture.selectIntersectingPortal(
        PortalTopology.links(entity.level()),
        sweep,
        anchor,
        last == null ? null : last.link().inverse(),
        link -> PortalAperture.touches(link.entrance().endpoint(), sweep) && convoyReady(entity, link));
  }

  private static boolean convoyReady(Entity entity, PortalLink link) {
    if (!PortalEntityMovement.canTraverse(entity, link)) return false;
    for (var passenger : entity.getPassengers()) if (!convoyReady(passenger, link)) return false;
    return true;
  }

  record LinkCrossing(PortalLink link, PortalCollisionWorld.Passage passage) {}

  static Optional<LinkCrossing> firstCrossing(
      Entity entity, List<AABB> hulls, Vec3 movement, AABB query) {
    LinkCrossing selected = null;
    double progress = Double.POSITIVE_INFINITY;
    Vec3 anchor = entity.getBoundingBox().getCenter();
    for (var link : PortalTopology.links(entity.level())) {
      if (PortalAperture.selectIntersectingPortal(List.of(link), query, anchor, null) == null
          || !convoyReady(entity, link)) continue;
      var scene = PortalCollisionWorld.query(entity, link, query);
      var crossing = scene.crossing(hulls, movement, link.entrance().endpoint());
      if (crossing.isEmpty()) continue;
      var at = crossing.orElseThrow().crossing();
      double distance = movement.subtract(at.remaining()).lengthSqr();
      if (distance < progress) {
        progress = distance;
        selected = new LinkCrossing(link, crossing.orElseThrow());
      }
    }
    return Optional.ofNullable(selected);
  }

  public static PortalLink intersectingPortal(Entity entity) {
    if (entity.noPhysics) return null;
    if (entity.isPassenger()) return intersectingPortal(entity.getRootVehicle());
    var scoped = EntityPortalState.of(entity).collisionLink;
    return scoped != null ? scoped : movementPortal(entity, Vec3.ZERO);
  }

  public static Boolean isInWall(Entity entity) {
    double width = entity.getBbWidth() * .8;
    AABB eye =
        AABB.ofSize(
            entity.getEyePosition().add(EntityPortalState.of(entity).eyeCorrection(entity.tickCount)),
            width,
            1e-6,
            width);
    return suffocates(entity, eye);
  }

  public static Boolean suffocates(Entity entity, AABB hull) {
    var link = intersectingPortal(entity);
    if (link == null) return null;
    var local = suffocating(entity, hull);
    var remote = suffocating(entity, link.transform().bounds(hull));
    return PortalCollisionWorld.material(link, local, remote).intersects(hull);
  }

  private static List<AABB> suffocating(Entity entity, AABB query) {
    var result = new ArrayList<AABB>();
    for (var pos : BlockPos.betweenClosed(query)) {
      var block = entity.level().getBlockState(pos);
      if (!block.isAir() && block.isSuffocating(entity.level(), pos))
        result.addAll(block.getCollisionShape(entity.level(), pos).move(pos).toAabbs());
    }
    return result;
  }

  public static boolean suppressFallDamage(Entity entity, boolean grounded) {
    var state = EntityPortalState.of(entity);
    if (!state.fallImmune) return false;
    entity.resetFallDistance();
    if (grounded) {
      var body = entity.getBoundingBox();
      double depth = PortalAperture.PLANE_EPSILON;
      var query = body.expandTowards(0, -depth, 0);
      var local = WorldCollisionShapes.boxes(entity, query);
      var link = intersectingPortal(entity, query, Vec3.ZERO);
      PortalGroundSupport support;
      if (link == null) {
        support =
            new PortalGroundSupport(
                local.stream().map(ConvexPolyhedron::box).toList(), body, depth);
      } else if (link.entrance().endpoint().normal().y == 0) {
        var portal = link.entrance().endpoint();
        var front = ConvexPolyhedron.Plane.through(portal.normal(), portal.center()).opposite();
        // The backing below the opening is a portal lip, not a landing outside the wall.
        support =
            new PortalGroundSupport(
                local.stream().map(ConvexPolyhedron::box).map(shape -> shape.clip(front)).toList(),
                body,
                depth);
      } else {
        support = PortalCollisionWorld.material(link, local, List.of()).groundSupport(body, depth);
      }
      if (support.supports(Vec3.ZERO)) state.fallImmune = false;
    }
    return true;
  }
}
