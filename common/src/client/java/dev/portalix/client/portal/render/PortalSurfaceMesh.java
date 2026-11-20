package dev.portalix.client.portal.render;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.geometry.PortalAperture;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

public final class PortalSurfaceMesh {
  public static final float UV_SCALE = PortalAperture.SURFACE_UV_SCALE;

  public static final float WIDTH = 1;
  public static final float HEIGHT = 2;
  public static final float SOURCE_X = WIDTH / 64;
  public static final float SOURCE_Y = HEIGHT / 112;
  public static final float NORMAL_OFFSET = .25F * SOURCE_X;

  private PortalSurfaceMesh() {}

  public record Vertex(Vec3 point, float u, float v) {}

  static boolean insideAperture(List<Vertex> polygon, float opening) {
    // Perspective-correct UVs stay in the convex hull of the clipped vertices.
    float radius = PortalOpeningAnimation.apertureScale(opening) - 1e-4F;
    if (radius <= 0) return false;
    for (var vertex : polygon) {
      float x = (2 * vertex.u() - 1) * UV_SCALE;
      float y = (2 * vertex.v() - 1) * UV_SCALE;
      if (x * x + y * y >= radius * radius) return false;
    }
    return true;
  }

  public static List<Vertex> mesh(PortalEndpoint portal, Vec3 camera, Matrix4fc projection) {
    var polygon = new ArrayList<Vertex>();
    for (int index = 0; index < 4; index++) polygon.add(corner(portal, camera, index));
    var planes =
        List.of(
            new Vector4f(0, 0, 0, 1),
            new Vector4f(1, 0, 0, 1),
            new Vector4f(-1, 0, 0, 1),
            new Vector4f(0, 1, 0, 1),
            new Vector4f(0, -1, 0, 1));
    for (var plane : planes) {
      if (polygon.isEmpty()) return List.of();
      var clipped = new ArrayList<Vertex>();
      var before = polygon.getLast();
      double a = projection.transform(new Vector4f(before.point.toVector3f(), 1)).dot(plane);
      for (var after : polygon) {
        double b = projection.transform(new Vector4f(after.point.toVector3f(), 1)).dot(plane);
        if ((a >= 0) != (b >= 0)) {
          double t = a / (a - b);
          clipped.add(
              new Vertex(
                  before.point.lerp(after.point, t),
                  (float) (before.u + (after.u - before.u) * t),
                  (float) (before.v + (after.v - before.v) * t)));
        }
        if (b >= 0) clipped.add(after);
        before = after;
        a = b;
      }
      polygon = clipped;
    }
    return List.copyOf(polygon);
  }

  static Vertex corner(PortalEndpoint portal, Vec3 origin, int index) {
    float u = index == 0 || index == 3 ? 0 : 1;
    float v = index < 2 ? 0 : 1;
    return new Vertex(point(portal, u, v).subtract(origin), u, v);
  }

  public static Vec3 point(PortalEndpoint portal, float u, float v) {
    return portal
        .center()
        .add(portal.right().scale((u - .5) * portal.width()))
        .add(portal.up().scale((.5 - v) * portal.height()));
  }
}
