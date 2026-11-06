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

  public static List<Vertex> mesh(PortalEndpoint portal, Vec3 camera, Matrix4fc projection) {
    var polygon = new ArrayList<Vertex>();
    for (int corner = 0; corner < 4; corner++) {
      float u = corner == 0 || corner == 3 ? 0 : 1;
      float v = corner < 2 ? 0 : 1;
      var point = point(portal, u, v).subtract(camera);
      polygon.add(new Vertex(point, u, v));
    }
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

  public static Vec3 point(PortalEndpoint portal, float u, float v) {
    return portal
        .center()
        .add(portal.right().scale((u - .5) * portal.width()))
        .add(portal.up().scale((.5 - v) * portal.height()));
  }
}
