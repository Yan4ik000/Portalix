package dev.portalix.portal.geometry;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.PortalLink;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PortalAperture {
  public static final double PLANE_EPSILON = 1e-5;
  public static final double CAVITY_DEPTH = 2.0;
  public static final double RIM_THICKNESS = 1.0 / 16.0;
  public static final float SURFACE_UV_SCALE = 1.075F;
  private static final double FLOOR_PASSAGE_SCALE = .9;
  private static final Vec3[] WORLD_AXES = {
    new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)
  };

  private PortalAperture() {}

  public static PortalLink selectIntersectingPortal(
      List<PortalLink> links, AABB hull, Vec3 anchor, PortalLink preferred) {
    return selectIntersectingPortal(links, hull, anchor, preferred, link -> true);
  }

  public static PortalLink selectIntersectingPortal(
      List<PortalLink> links,
      AABB hull,
      Vec3 anchor,
      PortalLink preferred,
      Predicate<PortalLink> eligible) {
    PortalLink selected = null;
    int bestRank = Integer.MAX_VALUE;
    double bestDistance = Double.POSITIVE_INFINITY;
    for (PortalLink link : links) {
      var entrance = link.entrance().endpoint();
      // The center belongs to the entrance space until movement transfers it across the plane.
      if (signedDistance(entrance, anchor) < -PLANE_EPSILON
          || !touches(entrance, hull.inflate(RIM_THICKNESS))
          || !eligible.test(link)) continue;
      int rank = (touches(entrance, hull) ? 0 : 4)
          + (contains(entrance, anchor) ? 0 : 2) + (link.equals(preferred) ? 0 : 1);
      double distance = Math.abs(signedDistance(entrance, anchor));
      if (rank < bestRank || rank == bestRank && Double.compare(distance, bestDistance) < 0) {
        selected = link;
        bestRank = rank;
        bestDistance = distance;
      }
    }
    return selected;
  }

  public static double signedDistance(PortalEndpoint portal, Vec3 point) {
    var center = portal.center();
    var normal = portal.normal();
    return (point.x - center.x) * normal.x
        + (point.y - center.y) * normal.y
        + (point.z - center.z) * normal.z;
  }

  public static boolean contains(PortalEndpoint portal, Vec3 point) {
    return contains(portal, point, portal.width() / 2, portal.height() / 2);
  }

  public static double passageHalfWidth(PortalEndpoint portal) {
    return portal.normal().y == 1
        ? portal.width() * FLOOR_PASSAGE_SCALE / (2 * SURFACE_UV_SCALE)
        : portal.width() / 2;
  }

  public static double passageHalfHeight(PortalEndpoint portal) {
    return portal.normal().y == 1
        ? portal.height() * FLOOR_PASSAGE_SCALE / (2 * SURFACE_UV_SCALE)
        : portal.height() / 2;
  }

  public static boolean containsPassage(PortalEndpoint portal, Vec3 point) {
    if (portal.normal().y == 1) return new PortalEllipse(portal, 0).metric(point) < 1 - 1e-9;
    return contains(portal, point, passageHalfWidth(portal), passageHalfHeight(portal));
  }

  private static boolean contains(
      PortalEndpoint portal, Vec3 point, double halfWidth, double halfHeight) {
    var center = portal.center();
    var right = portal.right();
    var up = portal.up();
    double x = point.x - center.x, y = point.y - center.y, z = point.z - center.z;
    return Math.abs(x * right.x + y * right.y + z * right.z) <= halfWidth
        && Math.abs(x * up.x + y * up.y + z * up.z) <= halfHeight;
  }

  public static double supportRadius(AABB hull, Vec3 axis) {
    return (Math.abs(axis.x) * hull.getXsize()
            + Math.abs(axis.y) * hull.getYsize()
            + Math.abs(axis.z) * hull.getZsize())
        / 2;
  }

  public static Optional<Vec3> fitHull(PortalEndpoint portal, AABB hull, Vec3 center) {
    if (portal.normal().y == 1) return Optional.of(new PortalEllipse(portal, 0).project(center));
    double right = passageHalfWidth(portal) - supportRadius(hull, portal.right());
    double up = passageHalfHeight(portal) - supportRadius(hull, portal.up());
    if (right < 0 || up < 0) return Optional.empty();
    Vec3 relative = center.subtract(portal.center());
    double x = relative.dot(portal.right()), y = relative.dot(portal.up());
    return Optional.of(
        center
            .add(portal.right().scale(Math.clamp(x, -right, right) - x))
            .add(portal.up().scale(Math.clamp(y, -up, up) - y)));
  }

  public static double passageHeight(PortalEndpoint portal, double width, double preferred) {
    Vec3[] axes = {portal.right(), portal.up()};
    double[] sizes = {portal.width(), portal.height()};
    double height = preferred;
    for (int i = 0; i < axes.length; i++) {
      Vec3 axis = axes[i];
      double vertical = Math.abs(axis.y);
      if (vertical > 1e-7)
        height =
            Math.min(height, (sizes[i] - width * (Math.abs(axis.x) + Math.abs(axis.z))) / vertical);
    }
    return height;
  }

  public static Vec3 shortestPathPoint(PortalEndpoint portal, Vec3 from, Vec3 to) {
    double first = Math.abs(signedDistance(portal, from));
    double second = Math.abs(signedDistance(portal, to));
    Vec3 point = from.lerp(to, first + second > 0 ? first / (first + second) : .5);
    point = point.subtract(portal.normal().scale(signedDistance(portal, point)));
    if (contains(portal, point)) return point;
    Vec3 best = null;
    double distance = Double.POSITIVE_INFINITY;
    for (int edge = 0; edge < 4; edge++) {
      boolean horizontal = edge < 2;
      Vec3 fixed = horizontal ? portal.up() : portal.right();
      Vec3 axis = horizontal ? portal.right() : portal.up();
      double extent = (horizontal ? portal.height() : portal.width()) / 2;
      double limit = (horizontal ? portal.width() : portal.height()) / 2;
      Vec3 base = portal.center().add(fixed.scale(edge % 2 == 0 ? -extent : extent));
      Vec3 a = from.subtract(base), b = to.subtract(base);
      double x = a.dot(axis), y = b.dot(axis);
      double h = Math.sqrt(Math.max(0, a.lengthSqr() - x * x));
      double k = Math.sqrt(Math.max(0, b.lengthSqr() - y * y));
      double along = h + k > 0 ? (k * x + h * y) / (h + k) : (x + y) / 2;
      Vec3 candidate = base.add(axis.scale(Math.clamp(along, -limit, limit)));
      double length = from.distanceTo(candidate) + candidate.distanceTo(to);
      if (length < distance) {
        best = candidate;
        distance = length;
      }
    }
    return best;
  }

  public static boolean touches(PortalEndpoint portal, AABB hull) {
    var center = portal.center();
    double extent = (portal.width() + portal.height()) / 2;
    if (hull.maxX < center.x - extent
        || hull.minX > center.x + extent
        || hull.maxY < center.y - extent
        || hull.minY > center.y + extent
        || hull.maxZ < center.z - extent
        || hull.minZ > center.z + extent) return false;
    Vec3 relative = hull.getCenter().subtract(portal.center());
    Vec3 right = portal.right();
    if (separates(portal, hull, relative, right, portal.normal())) return false;
    for (Vec3 axis : WORLD_AXES) {
      if (separates(portal, hull, relative, right, axis)
          || separates(portal, hull, relative, right, axis.cross(right))
          || separates(portal, hull, relative, right, axis.cross(portal.up()))) return false;
    }
    return true;
  }

  private static boolean separates(
      PortalEndpoint portal, AABB hull, Vec3 relative, Vec3 right, Vec3 axis) {
    double radius =
        supportRadius(hull, axis)
            + Math.abs(axis.dot(right)) * portal.width() / 2
            + Math.abs(axis.dot(portal.up())) * portal.height() / 2;
    return Math.abs(relative.dot(axis)) > radius + 1.0E-10;
  }

  public static Optional<Crossing> crossing(PortalEndpoint portal, Vec3 start, Vec3 displacement) {
    double distance = signedDistance(portal, start);
    double travel = displacement.dot(portal.normal());
    if (travel >= 0) return Optional.empty();
    if (distance < -PLANE_EPSILON) {
      Vec3 onPlane = start.subtract(portal.normal().scale(distance));
      return distance >= -CAVITY_DEPTH && distance + travel < 0 && containsPassage(portal, onPlane)
          ? Optional.of(new Crossing(0, onPlane, displacement))
          : Optional.empty();
    }
    if (distance + travel > 0) return Optional.empty();
    double fraction = Math.max(0, -distance / travel);
    Vec3 point = start.add(displacement.scale(fraction));
    return !containsPassage(portal, point)
        ? Optional.empty()
        : Optional.of(new Crossing(fraction, point, displacement.scale(1 - fraction)));
  }

  public record Crossing(double fraction, Vec3 point, Vec3 remaining) {}
}
