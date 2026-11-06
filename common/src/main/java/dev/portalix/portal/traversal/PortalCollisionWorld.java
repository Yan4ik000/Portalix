package dev.portalix.portal.traversal;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.geometry.ConvexPolyhedron;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.geometry.PortalCollisionOpening;
import dev.portalix.portal.geometry.PortalEllipse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PortalCollisionWorld {
  private static final List<Vec3> WORLD_AXES =
      List.of(new Vec3(0, 1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1));
  private final List<ConvexPolyhedron> obstacles;
  private final List<ConvexPolyhedron> floorMaterial;
  private final PortalEndpoint floorOpening;
  private final List<Vec3> axes;

  private PortalCollisionWorld(
      List<ConvexPolyhedron> obstacles,
      List<ConvexPolyhedron> floorMaterial,
      List<Vec3> axes,
      PortalEndpoint floorOpening) {
    this.obstacles = List.copyOf(obstacles);
    this.floorMaterial = List.copyOf(floorMaterial);
    this.floorOpening = floorOpening;
    this.axes = axes;
  }

  public static PortalCollisionWorld query(Entity entity, PortalLink link, AABB query) {
    var local = WorldCollisionShapes.boxes(entity, query);
    var remote = WorldCollisionShapes.boxes(entity, link.transform().bounds(query));
    return create(
        link,
        local,
        remote,
        PortalTopology.collisionBoundary(entity.level(), link.entrance().endpoint()));
  }

  public static PortalGroundSupport queryGroundSupport(
      Entity entity, PortalLink link, AABB query, AABB body, double depth) {
    return query(entity, link, query).groundSupport(body, depth);
  }

  public PortalCollisionWorld blockCrossing(PortalEndpoint portal, AABB hull, AABB query) {
    Vec3 boundary =
        portal
            .center()
            .subtract(portal.normal().scale(PortalAperture.supportRadius(hull, portal.normal())));
    var barrier =
        ConvexPolyhedron.box(
                query.expandTowards(
                    portal
                        .normal()
                        .scale(-2 * PortalAperture.supportRadius(hull, portal.normal()))))
            .clip(ConvexPolyhedron.Plane.through(portal.normal(), boundary));
    var blocked = new ArrayList<>(obstacles);
    if (!barrier.isEmpty()) blocked.add(barrier);
    return new PortalCollisionWorld(blocked, floorMaterial, axes, floorOpening);
  }

  public static PortalCollisionWorld material(
      PortalLink link, List<AABB> local, List<AABB> remote) {
    return create(link, local, remote, List.of());
  }

  public static PortalCollisionWorld solid(List<AABB> boxes) {
    return new PortalCollisionWorld(
        boxes.stream().map(ConvexPolyhedron::box).toList(), List.of(), WORLD_AXES, null);
  }

  private static PortalCollisionWorld create(
      PortalLink link, List<AABB> local, List<AABB> remote, List<ConvexPolyhedron> boundary) {
    var entrance = link.entrance().endpoint();
    boolean floor = entrance.normal().y == 1;
    var obstacles = new ArrayList<ConvexPolyhedron>();
    var floorMaterial = new ArrayList<ConvexPolyhedron>();
    var openingPlanes =
        !remote.isEmpty() || !floor && !local.isEmpty()
            ? PortalCollisionOpening.planes(entrance)
            : List.<ConvexPolyhedron.Plane>of();
    if (!floor) obstacles.addAll(boundary);
    for (AABB box : local) {
      if (floor) {
        var plane = ConvexPolyhedron.Plane.through(entrance.normal(), entrance.center());
        var solid = ConvexPolyhedron.box(box);
        var front = solid.clip(plane.opposite());
        if (!front.isEmpty()) {
          obstacles.add(front);
        }
        var behind = solid.clip(plane);
        var bottom =
            ConvexPolyhedron.Plane.through(
                entrance.normal(),
                entrance.center().subtract(entrance.normal().scale(PortalAperture.CAVITY_DEPTH)));
        var deep = behind.clip(bottom);
        if (!deep.isEmpty()) {
          obstacles.add(deep);
        }
        var cavity = behind.clip(bottom.opposite());
        if (!cavity.isEmpty()) floorMaterial.add(cavity);
      } else {
        obstacles.addAll(PortalCollisionOpening.subtract(ConvexPolyhedron.box(box), openingPlanes));
      }
    }
    var inverse = remote.isEmpty() ? null : link.transform().inverse();
    for (AABB box : remote) {
      var remoteHalf = ConvexPolyhedron.box(box).transform(inverse);
      for (var plane : openingPlanes) remoteHalf = remoteHalf.clip(plane);
      if (!remoteHalf.isEmpty()) {
        obstacles.add(remoteHalf);
      }
    }
    var axes =
        entrance.normal().x == 0 && entrance.normal().z == 0
            ? List.of(WORLD_AXES.getFirst(), entrance.right(), entrance.up())
            : WORLD_AXES;
    return new PortalCollisionWorld(obstacles, floorMaterial, axes, floor ? entrance : null);
  }

  public List<Vec3> path(AABB box, Vec3 movement) {
    return resolve(List.of(box), movement).points();
  }

  private Vec3 collide(List<AABB> hulls, Vec3 movement) {
    return resolve(hulls, movement).movement();
  }

  public Optional<Passage> crossing(List<AABB> hulls, Vec3 movement, PortalEndpoint portal) {
    Vec3 anchor = hulls.getFirst().getCenter();
    var candidate = PortalAperture.crossing(portal, anchor, movement);
    if (candidate.isPresent()) {
      var at = candidate.orElseThrow();
      Vec3 prefix = movement.scale(at.fraction());
      Vec3 actual = collide(hulls, prefix);
      Vec3 reached = anchor.add(actual);
      double distance = PortalAperture.signedDistance(portal, reached);
      if (actual.distanceToSqr(prefix) <= 1e-18
          && Math.abs(distance) <= PortalAperture.PLANE_EPSILON
          && PortalAperture.containsPassage(portal, reached))
        return Optional.of(
            new Passage(new PortalAperture.Crossing(at.fraction(), reached, at.remaining()), true));
    }
    var sliding = resolve(hulls, movement, portal).crossing();
    return sliding == null ? Optional.empty() : Optional.of(new Passage(sliding, false));
  }

  public record Passage(PortalAperture.Crossing crossing, boolean straight) {}

  private Resolved resolve(List<AABB> hulls, Vec3 movement) {
    return resolve(hulls, movement, null);
  }

  private Resolved resolve(List<AABB> hulls, Vec3 movement, PortalEndpoint portal) {
    Vec3 anchor = hulls.getFirst().getCenter();
    if (floorOpening == null && clearSweep(hulls, movement)) {
      var crossing =
          portal == null ? null : PortalAperture.crossing(portal, anchor, movement).orElse(null);
      return new Resolved(
          List.of(anchor, anchor.add(movement)),
          crossing == null ? movement : crossing.point().subtract(anchor),
          crossing);
    }
    var points = new ArrayList<Vec3>();
    points.add(anchor);
    Vec3 resolved = Vec3.ZERO, requested = Vec3.ZERO, clipped = Vec3.ZERO;
    var order =
        Math.abs(movement.dot(axes.get(1))) < Math.abs(movement.dot(axes.get(2)))
            ? List.of(axes.get(0), axes.get(2), axes.get(1))
            : axes;
    for (Vec3 axis : order) {
      double distance = movement.dot(axis);
      if (distance == 0) continue;
      Vec3 requestedSegment = axis.scale(distance);
      Vec3 segment = requestedSegment;
      if (floorOpening != null) {
        var ellipse = new PortalEllipse(floorOpening, 0);
        var confined = new ArrayList<Vec3>();
        for (var box : hulls) {
          Vec3 center = box.getCenter().add(resolved);
          if (box.minY + resolved.y < floorOpening.center().y - PortalAperture.PLANE_EPSILON
              && box.maxY + resolved.y > floorOpening.center().y - PortalAperture.CAVITY_DEPTH
              && ellipse.metric(center) <= 1 + 1e-10) {
            confined.add(center);
            Vec3 destination = center.add(segment);
            segment = segment.add(ellipse.project(destination).subtract(destination));
          }
        }
        double permitted = 1;
        for (var center : confined) {
          var interval = ellipse.interval(center, segment);
          if (interval.isPresent())
            permitted = Math.min(permitted, Math.max(0, interval.orElseThrow().leave()));
        }
        segment = segment.scale(permitted);
      }
      double fraction = 1;
      for (var box : hulls) {
        var moved = box.move(resolved);
        var hull = ConvexPolyhedron.box(moved);
        for (var obstacle : obstacles) {
          var hit = hull.sweep(segment, obstacle);
          if (hit.isPresent()
              && (!hit.get().startedInside() || segment.dot(hit.get().normal()) < 0))
            fraction = Math.min(fraction, hit.get().fraction());
        }
        if (floorOpening != null
            && !PortalAperture.containsPassage(floorOpening, moved.getCenter())) {
          for (var obstacle : floorMaterial) {
            var hit = hull.sweep(segment, obstacle);
            if (hit.isPresent()
                && (!hit.get().startedInside() || segment.dot(hit.get().normal()) < 0))
              fraction = Math.min(fraction, hit.get().fraction());
          }
        }
      }
      Vec3 actual = segment.scale(fraction);
      if (portal != null) {
        var crossing = PortalAperture.crossing(portal, anchor.add(resolved), actual);
        if (crossing.isPresent()) {
          var at = crossing.orElseThrow();
          Vec3 prefix = requested.add(actual.scale(at.fraction()));
          double time =
              Math.clamp(
                  -PortalAperture.signedDistance(portal, anchor) / movement.dot(portal.normal()),
                  0,
                  1);
          return new Resolved(
              List.copyOf(points),
              at.point().subtract(anchor),
              new PortalAperture.Crossing(time, at.point(), movement.subtract(prefix)));
        }
      }
      requested = requested.add(requestedSegment);
      clipped = clipped.add(requestedSegment.subtract(actual));
      resolved = requested.subtract(clipped);
      if (fraction > 0) points.add(anchor.add(resolved));
    }
    return new Resolved(List.copyOf(points), movement.subtract(clipped), null);
  }

  private boolean clearSweep(List<AABB> hulls, Vec3 movement) {
    for (var box : hulls) {
      var hull = ConvexPolyhedron.box(box);
      for (var obstacle : obstacles) {
        var hit = hull.sweep(movement, obstacle);
        if (hit.isPresent()
            && (!hit.orElseThrow().startedInside() || movement.dot(hit.orElseThrow().normal()) < 0))
          return false;
      }
    }
    return true;
  }

  private record Resolved(List<Vec3> points, Vec3 movement, PortalAperture.Crossing crossing) {}

  public double sweepFraction(AABB box, Vec3 movement) {
    double fraction = sweepFraction(box, movement, obstacles, 0, 1, true);
    if (floorOpening == null || floorMaterial.isEmpty()) return fraction;
    var ellipse = new PortalEllipse(floorOpening, 0);
    double enter = 0, leave = 1;
    for (double x : new double[] {box.minX, box.maxX}) {
      for (double z : new double[] {box.minZ, box.maxZ}) {
        var interval = ellipse.interval(new Vec3(x, box.getCenter().y, z), movement);
        if (interval.isEmpty())
          return Math.min(fraction, sweepFraction(box, movement, floorMaterial, 0, 1, true));
        enter = Math.max(enter, interval.orElseThrow().enter());
        leave = Math.min(leave, interval.orElseThrow().leave());
      }
    }
    if (enter >= leave)
      return Math.min(fraction, sweepFraction(box, movement, floorMaterial, 0, 1, true));
    if (enter > 0)
      fraction =
          Math.min(fraction, sweepFraction(box, movement, floorMaterial, 0, Math.min(1, enter), true));
    // Material outside the opening becomes an obstacle at this boundary, not an overlap to escape.
    if (leave < 1)
      fraction =
          Math.min(fraction, sweepFraction(box, movement, floorMaterial, Math.max(0, leave), 1, false));
    return fraction;
  }

  private static double sweepFraction(
      AABB box,
      Vec3 movement,
      List<ConvexPolyhedron> material,
      double start,
      double end,
      boolean allowEscape) {
    if (start >= end) return 1;
    var hull = ConvexPolyhedron.box(box.move(movement.scale(start)));
    var segment = movement.scale(end - start);
    double fraction = 1;
    for (var obstacle : material) {
      var hit = hull.sweep(segment, obstacle);
      if (hit.isPresent()
          && (!hit.orElseThrow().startedInside()
              || !allowEscape
              || segment.dot(hit.orElseThrow().normal()) <= 0))
        fraction = Math.min(fraction, start + (end - start) * hit.orElseThrow().fraction());
    }
    return fraction;
  }

  public boolean contains(Vec3 point) {
    return obstacles.stream().anyMatch(hull -> hull.contains(point))
        || floorOpening != null
            && !PortalAperture.containsPassage(floorOpening, point)
            && floorMaterial.stream().anyMatch(hull -> hull.contains(point));
  }

  public boolean intersects(AABB box) {
    var hull = ConvexPolyhedron.box(box);
    return obstacles.stream().anyMatch(hull::intersects)
        || floorOpening != null
            && !PortalAperture.containsPassage(floorOpening, box.getCenter())
            && floorMaterial.stream().anyMatch(hull::intersects);
  }

  public PortalGroundSupport groundSupport(AABB body, double depth) {
    var support = new PortalGroundSupport(obstacles, body, depth);
    if (floorOpening == null) return support;
    return support.union(
        new PortalGroundSupport(floorMaterial, body, depth)
            .outsideOpening(floorOpening, body, depth, false));
  }

  public Vec3 collide(List<AABB> hulls, Vec3 movement, double maxStep, boolean onGround) {
    AABB box = hulls.getFirst();
    Vec3 result = collide(hulls, movement);
    boolean grounded = result.y != movement.y && movement.y < 0;
    if (maxStep <= 0
        || (!grounded && !onGround)
        || (result.x == movement.x && result.z == movement.z)) return result;
    AABB groundedBox = grounded ? box.move(0, result.y, 0) : box;
    var groundedHulls =
        grounded ? hulls.stream().map(hull -> hull.move(0, result.y, 0)).toList() : hulls;
    var heights = new TreeSet<Double>();
    var stepObstacles = new ArrayList<>(obstacles);
    stepObstacles.addAll(floorMaterial);
    for (var obstacle : stepObstacles)
      for (Vec3 vertex : obstacle.vertices()) {
        double height = vertex.y - groundedBox.minY;
        if (height >= 0 && height <= maxStep && height != result.y) heights.add(height);
      }
    for (double height : heights) {
      Vec3 step = collide(groundedHulls, new Vec3(movement.x, height, movement.z));
      if (step.horizontalDistanceSqr() > result.horizontalDistanceSqr())
        return step.subtract(0, box.minY - groundedBox.minY, 0);
    }
    return result;
  }
}
