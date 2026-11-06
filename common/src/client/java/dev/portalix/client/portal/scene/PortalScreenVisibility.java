package dev.portalix.client.portal.scene;

import dev.portalix.api.PortalEndpoint;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

final class PortalScreenVisibility {
  static final Bounds SCREEN = new Bounds(-1, -1, 1, 1);

  private PortalScreenVisibility() {}

  static Bounds project(
      PortalEndpoint portal, CameraRenderState camera, Matrix4fc projection, Matrix4fc modelView,
      Bounds parent) {
    if (parent == null || camera.pos.subtract(portal.center()).dot(portal.normal()) < -.15)
      return null;
    var relative = camera.pos.subtract(portal.center());
    if (Math.abs(relative.dot(portal.normal())) < .15
        && Math.abs(relative.dot(portal.right())) <= portal.width() / 2
        && Math.abs(relative.dot(portal.up())) <= portal.height() / 2) return parent;
    Matrix4f matrix = new Matrix4f(projection).mul(modelView);
    List<Vector4f> polygon = new ArrayList<>();
    int[] xs = {-1, 1, 1, -1};
    int[] ys = {-1, -1, 1, 1};
    for (int i = 0; i < 4; i++) {
      var corner =
          portal
              .center()
              .add(portal.right().scale(xs[i] * portal.width() / 2))
              .add(portal.up().scale(ys[i] * portal.height() / 2))
              .subtract(camera.pos);
      polygon.add(
          matrix.transform(new Vector4f((float) corner.x, (float) corner.y, (float) corner.z, 1)));
    }

    polygon = clip(polygon, new Vector4f(0, 0, 0, 1), 1.0e-5F);
    polygon = clip(polygon, new Vector4f(1, 0, 0, (float) -parent.left), 0);
    polygon = clip(polygon, new Vector4f(-1, 0, 0, (float) parent.right), 0);
    polygon = clip(polygon, new Vector4f(0, 1, 0, (float) -parent.bottom), 0);
    polygon = clip(polygon, new Vector4f(0, -1, 0, (float) parent.top), 0);
    if (polygon.isEmpty()) return null;
    double left = 1, bottom = 1, right = -1, top = -1;
    for (Vector4f vertex : polygon) {
      double x = vertex.x / vertex.w, y = vertex.y / vertex.w;
      left = Math.min(left, x);
      bottom = Math.min(bottom, y);
      right = Math.max(right, x);
      top = Math.max(top, y);
    }
    return new Bounds(left, bottom, right, top);
  }

  private static List<Vector4f> clip(List<Vector4f> polygon, Vector4f plane, float threshold) {
    List<Vector4f> result = new ArrayList<>();
    if (polygon.isEmpty()) return result;
    Vector4f before = polygon.getLast();
    float a = before.dot(plane) - threshold;
    for (Vector4f after : polygon) {
      float b = after.dot(plane) - threshold;
      if ((a >= 0) != (b >= 0)) result.add(new Vector4f(before).lerp(after, a / (a - b)));
      if (b >= 0) result.add(after);
      before = after;
      a = b;
    }
    return result;
  }

  record Bounds(double left, double bottom, double right, double top) {}
}
