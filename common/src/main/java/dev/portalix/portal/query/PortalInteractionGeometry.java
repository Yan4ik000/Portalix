package dev.portalix.portal.query;

import dev.portalix.portal.PortalLink;
import dev.portalix.portal.geometry.ConvexPolyhedron;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PortalInteractionGeometry {
  private PortalInteractionGeometry() {}

  public static Optional<Vec3> closest(Vec3 eye, AABB target, List<PortalLink> route) {
    Vec3 virtualEye = eye;
    for (var link : route) virtualEye = link.transform().point(virtualEye);
    var visible = ConvexPolyhedron.box(target);
    Vec3 previousNormal = null;
    double previousDistance = 0;
    for (int gate = 0; gate < route.size(); gate++) {
      var portal = route.get(gate).entrance().endpoint();
      Vec3 center = portal.center();
      Vec3 right = portal.up().cross(portal.normal()).scale(.5);
      Vec3 up = portal.up();
      Vec3 normal = portal.normal();
      for (int i = gate; i < route.size(); i++) {
        var transform = route.get(i).transform();
        center = transform.point(center);
        right = transform.vector(right);
        up = transform.vector(up);
        normal = transform.vector(normal);
      }
      double distance = virtualEye.subtract(center).dot(normal);
      if (distance <= 0) return Optional.empty();
      if (previousNormal != null) {
        Vec3 order = previousNormal.scale(distance).subtract(normal.scale(previousDistance));
        if (order.lengthSqr() > 1e-20) {
          order = order.normalize();
          visible = visible.clip(new ConvexPolyhedron.Plane(order, order.dot(virtualEye)));
        }
      }
      previousNormal = normal;
      previousDistance = distance;
      visible = visible.clip(new ConvexPolyhedron.Plane(normal, normal.dot(center)));
      var corners =
          List.of(
              center.add(right).add(up),
              center.subtract(right).add(up),
              center.subtract(right).subtract(up),
              center.add(right).subtract(up));
      for (int edge = 0; edge < 4; edge++) {
        Vec3 side =
            corners
                .get(edge)
                .subtract(virtualEye)
                .cross(corners.get((edge + 1) % 4).subtract(virtualEye))
                .normalize();
        if (side.dot(center.subtract(virtualEye)) > 0) side = side.scale(-1);
        visible = visible.clip(new ConvexPolyhedron.Plane(side, side.dot(virtualEye)));
      }
    }
    return visible.closestPoint(virtualEye);
  }
}
