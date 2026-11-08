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
    return project(portal, camera, new Matrix4f(projection).mul(modelView), parent);
  }

  static Bounds project(
      PortalEndpoint portal, CameraRenderState camera, Matrix4fc viewProjection, Bounds parent) {
    if (parent == null) return null;
    var relative = camera.pos.subtract(portal.center());
    double distance = relative.dot(portal.normal());
    if (distance < -.15) return null;
    var portalRight = portal.right();
    if (Math.abs(distance) < .15
        && Math.abs(relative.dot(portalRight)) <= portal.width() / 2
        && Math.abs(relative.dot(portal.up())) <= portal.height() / 2) return parent;
    List<Vector4f> polygon = new ArrayList<>(4);
    var rightAxis = portalRight.scale(portal.width() / 2);
    var upAxis = portal.up().scale(portal.height() / 2);
    for (int i = 0; i < 4; i++) {
      var corner =
          portal
              .center()
              .add(i == 0 || i == 3 ? rightAxis.reverse() : rightAxis)
              .add(i < 2 ? upAxis.reverse() : upAxis)
              .subtract(camera.pos);
      polygon.add(
          viewProjection.transform(new Vector4f((float) corner.x, (float) corner.y, (float) corner.z, 1)));
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
