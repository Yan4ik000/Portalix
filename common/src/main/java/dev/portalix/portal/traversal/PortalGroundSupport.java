package dev.portalix.portal.traversal;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.geometry.ConvexPolyhedron;
import dev.portalix.portal.geometry.ConvexPolyhedron.Plane;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.geometry.PortalEllipse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PortalGroundSupport {
  private static final double INSET = 1e-7;
  private static final double EPSILON = 1e-9;
  private final List<Region> regions;
  private final Vec3 origin;

  private PortalGroundSupport(List<Region> regions, Vec3 origin) {
    this.regions = List.copyOf(regions);
    this.origin = origin;
  }

  public PortalGroundSupport(List<ConvexPolyhedron> obstacles, AABB body, double depth) {
    origin = body.getCenter();
    var support = new ArrayList<Region>();
    double halfX = body.getXsize() / 2 - INSET;
    double halfZ = body.getZsize() / 2 - INSET;
    for (var obstacle : obstacles) {
      var slice =
          obstacle
              .clip(new Plane(new Vec3(0, 1, 0), body.minY))
              .clip(new Plane(new Vec3(0, -1, 0), -body.minY + depth + INSET));
      if (slice.isEmpty()) continue;
      var points = new ArrayList<Vec3>();
      for (var vertex : slice.vertices()) {
        for (double x : new double[] {-halfX, halfX})
          for (double z : new double[] {-halfZ, halfZ})
            points.add(new Vec3(vertex.x - origin.x + x, 0, vertex.z - origin.z + z));
      }
      var polygon = convexHull(points);
      if (polygon.size() < 3) continue;
      var planes = new ArrayList<Plane>();
      for (int i = 0; i < polygon.size(); i++) {
        Vec3 start = polygon.get(i);
        Vec3 edge = polygon.get((i + 1) % polygon.size()).subtract(start);
        Vec3 normal = new Vec3(edge.z, 0, -edge.x).normalize();
        planes.add(new Plane(normal, normal.dot(start) - INSET));
      }
      support.add(new Region(List.copyOf(planes), null));
    }
    regions = List.copyOf(support);
  }

  PortalGroundSupport union(PortalGroundSupport other) {
    var combined = new ArrayList<>(regions);
    combined.addAll(other.regions);
    return new PortalGroundSupport(combined, origin);
  }

  public boolean supports(Vec3 displacement) {
    return regions.stream().anyMatch(region -> contains(region, displacement));
  }

  public PortalGroundSupport outsideOpening(PortalEndpoint portal, AABB body, double depth) {
    return outsideOpening(portal, body, depth, true);
  }

  PortalGroundSupport outsideOpening(
      PortalEndpoint portal, AABB body, double depth, boolean allowEscape) {
    if (portal.normal().y != 1) return this;
    if (allowEscape
        && (body.minY < portal.center().y - PortalAperture.PLANE_EPSILON
            || body.minY - depth > portal.center().y)) return this;
    var ellipse = new PortalEllipse(portal, allowEscape ? INSET : 0);
    var restricted = new ArrayList<Region>();
    if (allowEscape && ellipse.metric(origin) < 1) {
      Vec3 normal = ellipse.normal(origin).scale(-1);
      if (normal.lengthSqr() == 0) return this;
      for (var region : regions) {
        var planes = new ArrayList<>(region.planes());
        planes.add(new Plane(normal, 0));
        restricted.add(new Region(List.copyOf(planes), region.hole()));
      }
    } else {
      for (var region : regions) restricted.add(new Region(region.planes(), ellipse));
    }
    return new PortalGroundSupport(restricted, origin);
  }

  public Vec3 backOff(Vec3 movement) {
    if (!supports(Vec3.ZERO)) return movement;
    Vec3 horizontal = new Vec3(movement.x, 0, movement.z);
    double fraction = supportedFraction(Vec3.ZERO, horizontal);
    if (fraction == 1) return movement;
    Vec3 reached = horizontal.scale(fraction);
    Vec3 remaining = horizontal.scale(1 - fraction);
    Vec3 best = Vec3.ZERO;
    double bestProgress = 0;
    for (var region : regions) {
      if (!contains(region, reached)) continue;
      var normals = new ArrayList<Vec3>();
      for (var plane : region.planes())
        if (Math.abs(plane.normal().dot(reached) - plane.offset()) <= EPSILON)
          normals.add(plane.normal());
      if (region.hole() != null && Math.abs(region.hole().metric(origin.add(reached)) - 1) <= 1e-7)
        normals.add(region.hole().normal(origin.add(reached)).scale(-1));
      for (var normal : normals) {
        double outward = remaining.dot(normal);
        if (outward <= 0) continue;
        Vec3 slide = remaining.subtract(normal.scale(outward));
        slide = slide.scale(supportedFraction(reached, slide));
        double progress = slide.dot(remaining);
        if (progress > bestProgress) {
          best = slide;
          bestProgress = progress;
        }
      }
    }
    return new Vec3(reached.x + best.x, movement.y, reached.z + best.z);
  }

  private double supportedFraction(Vec3 start, Vec3 movement) {
    var intervals = new ArrayList<Interval>();
    for (var region : regions) {
      double enter = 0, leave = 1;
      for (var plane : region.planes()) {
        double distance = plane.offset() - plane.normal().dot(start);
        double speed = plane.normal().dot(movement);
        if (Math.abs(speed) <= EPSILON) {
          if (distance < -EPSILON) {
            leave = -1;
            break;
          }
        } else if (speed > 0) leave = Math.min(leave, distance / speed);
        else enter = Math.max(enter, distance / speed);
      }
      if (enter > leave || leave < 0 || enter > 1) continue;
      var hole =
          region.hole() == null
              ? Optional.<PortalEllipse.Interval>empty()
              : region.hole().interval(origin.add(start), movement);
      if (hole.isEmpty()) intervals.add(new Interval(enter, leave));
      else {
        double first = Math.min(leave, hole.orElseThrow().enter());
        double last = Math.max(enter, hole.orElseThrow().leave());
        if (enter <= first) intervals.add(new Interval(enter, first));
        if (last <= leave) intervals.add(new Interval(last, leave));
      }
    }
    intervals.sort(Comparator.comparingDouble(Interval::enter));
    double fraction = 0;
    for (var interval : intervals) {
      if (interval.enter() > fraction + EPSILON) break;
      fraction = Math.max(fraction, interval.leave());
    }
    return Math.clamp(fraction, 0, 1);
  }

  private boolean contains(Region region, Vec3 point) {
    return region.planes().stream()
            .allMatch(plane -> plane.normal().dot(point) <= plane.offset() + EPSILON)
        && (region.hole() == null || region.hole().metric(origin.add(point)) >= 1 - EPSILON);
  }

  private static List<Vec3> convexHull(List<Vec3> points) {
    points.sort(Comparator.comparingDouble((Vec3 p) -> p.x).thenComparingDouble(p -> p.z));
    var hull = new ArrayList<Vec3>();
    for (var point : points) {
      while (hull.size() >= 2 && turn(hull.get(hull.size() - 2), hull.getLast(), point) <= 0)
        hull.removeLast();
      hull.add(point);
    }
    int lower = hull.size();
    for (int i = points.size() - 2; i >= 0; i--) {
      Vec3 point = points.get(i);
      while (hull.size() > lower && turn(hull.get(hull.size() - 2), hull.getLast(), point) <= 0)
        hull.removeLast();
      hull.add(point);
    }
    hull.removeLast();
    return hull;
  }

  private static double turn(Vec3 a, Vec3 b, Vec3 c) {
    return (b.x - a.x) * (c.z - a.z) - (b.z - a.z) * (c.x - a.x);
  }

  private record Region(List<Plane> planes, PortalEllipse hole) {}

  private record Interval(double enter, double leave) {}
}
