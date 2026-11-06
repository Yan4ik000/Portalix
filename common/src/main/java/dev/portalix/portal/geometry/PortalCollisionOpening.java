package dev.portalix.portal.geometry;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.geometry.ConvexPolyhedron.Plane;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PortalCollisionOpening {
  private static final class Boundary {
    static final List<ConvexPolyhedron> SHAPES = create();

    private static List<ConvexPolyhedron> create() {
      double rim = PortalAperture.RIM_THICKNESS;
      return subtract(
          ConvexPolyhedron.box(
              new AABB(
                  -.5 - rim, -1 - rim, -PortalAperture.CAVITY_DEPTH - rim, .5 + rim, 1 + rim, 0)),
          List.of(
              new Plane(new Vec3(1, 0, 0), .5),
              new Plane(new Vec3(-1, 0, 0), .5),
              new Plane(new Vec3(0, 1, 0), 1),
              new Plane(new Vec3(0, -1, 0), 1),
              new Plane(new Vec3(0, 0, 1), 0),
              new Plane(new Vec3(0, 0, -1), PortalAperture.CAVITY_DEPTH)));
    }
  }

  private PortalCollisionOpening() {}

  public static List<Plane> planes(PortalEndpoint portal) {
    Vec3 right = portal.right(),
        up = portal.up(),
        normal = portal.normal(),
        center = portal.center();
    return List.of(
        new Plane(right, right.dot(center) + PortalAperture.passageHalfWidth(portal)),
        new Plane(right.scale(-1), -right.dot(center) + PortalAperture.passageHalfWidth(portal)),
        new Plane(up, up.dot(center) + PortalAperture.passageHalfHeight(portal)),
        new Plane(up.scale(-1), -up.dot(center) + PortalAperture.passageHalfHeight(portal)),
        new Plane(normal, normal.dot(center)),
        new Plane(normal.scale(-1), -normal.dot(center) + PortalAperture.CAVITY_DEPTH));
  }

  public static List<ConvexPolyhedron> subtract(ConvexPolyhedron original, List<Plane> cavity) {
    var result = new ArrayList<ConvexPolyhedron>();
    ConvexPolyhedron remainder = original;
    for (Plane plane : cavity) {
      ConvexPolyhedron outside = remainder.clip(plane.opposite());
      if (!outside.isEmpty()) result.add(outside);
      remainder = remainder.clip(plane);
      if (remainder.isEmpty()) break;
    }
    return List.copyOf(result);
  }

  public static List<ConvexPolyhedron> boundary(PortalEndpoint portal) {
    if (portal.normal().y == 1) return List.of();
    var local =
        new PortalEndpoint(
            portal.dimension(), Vec3.ZERO, new Vec3(0, 0, 1), new Vec3(0, 1, 0), 1, 2);
    var destination =
        new PortalEndpoint(
            portal.dimension(), portal.center(), portal.normal().scale(-1), portal.up(), 1, 2);
    var transform = new PortalTransform(local, destination);
    return Boundary.SHAPES.stream().map(shape -> shape.transform(transform)).toList();
  }
}
