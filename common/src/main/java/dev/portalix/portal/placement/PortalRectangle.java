package dev.portalix.portal.placement;

import static dev.portalix.portal.placement.PortalSurface.EPSILON;

import dev.portalix.api.PortalEndpoint;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.phys.Vec3;

record PortalRectangle(Vec3 center, Vec3 right, Vec3 up, double halfWidth, double halfHeight) {
  record Contact(Vec3 point, Vec3 normal, double fraction) {}

  static PortalRectangle of(PortalEndpoint endpoint) {
    return new PortalRectangle(
        endpoint.center(),
        endpoint.right(),
        endpoint.up(),
        endpoint.width() / 2,
        endpoint.height() / 2);
  }

  PortalRectangle at(Vec3 position) {
    return new PortalRectangle(position, right, up, halfWidth, halfHeight);
  }

  double extent(Vec3 axis) {
    return Math.abs(right.dot(axis)) * halfWidth + Math.abs(up.dot(axis)) * halfHeight;
  }

  List<Vec3> axes(PortalRectangle other) {
    return List.of(right, up, other.right, other.up);
  }

  boolean overlaps(PortalRectangle other) {
    Vec3 delta = other.center.subtract(center);
    for (Vec3 axis : axes(other)) {
      if (Math.abs(delta.dot(axis)) >= extent(axis) + other.extent(axis) - EPSILON) return false;
    }
    return true;
  }

  Contact trace(Vec3 start, Vec3 end) {
    Vec3 offset = start.subtract(center), movement = end.subtract(start);
    double enter = 0, exit = 1;
    Vec3 normal = Vec3.ZERO;
    for (Vec3 axis : List.of(right, up)) {
      double origin = offset.dot(axis), delta = movement.dot(axis), extent = extent(axis);
      if (Math.abs(delta) < EPSILON) {
        if (Math.abs(origin) >= extent - EPSILON) return null;
        continue;
      }
      double a = (-extent - origin) / delta, b = (extent - origin) / delta;
      double near = Math.min(a, b), far = Math.max(a, b);
      if (near > enter + EPSILON) normal = Vec3.ZERO;
      enter = Math.max(enter, near);
      if (near >= enter - EPSILON) {
        Vec3 candidate = axis.scale(delta > 0 ? -1 : 1);
        normal = candidate;
      }
      exit = Math.min(exit, far);
      if (enter >= exit - EPSILON) return null;
    }
    if (enter >= 1 - EPSILON || normal == Vec3.ZERO) return null;
    return new Contact(start.add(movement.scale(enter)), normal, enter);
  }

  Contact traceBoundary(Vec3 start, Vec3 end, Predicate<Vec3> exposed) {
    Vec3 movement = end.subtract(start);
    Contact first = null;
    for (int i = 0; i < 2; i++) {
      Vec3 axis = i == 0 ? right : up;
      Vec3 tangent = i == 0 ? up : right;
      for (int sign : new int[] {1, -1}) {
        Vec3 normal = axis.scale(sign);
        double speed = movement.dot(normal);
        if (speed >= -EPSILON || !exposed.test(normal)) continue;
        double fraction = (center.subtract(start).dot(normal) + extent(axis)) / speed;
        if (fraction < -EPSILON || fraction >= 1 - EPSILON) continue;
        fraction = Math.max(0, fraction);
        Vec3 point = start.add(movement.scale(fraction));
        if (Math.abs(point.subtract(center).dot(tangent)) > extent(tangent) + EPSILON) continue;
        if (first == null || fraction < first.fraction)
          first = new Contact(point, normal, fraction);
      }
    }
    return first;
  }
}
