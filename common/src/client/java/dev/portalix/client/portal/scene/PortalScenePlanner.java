package dev.portalix.client.portal.scene;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.PortalLink;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;

final class PortalScenePlanner {
  private final List<PortalLink> links;
  private final Matrix4fc projection;
  private final Matrix4f viewEffects;
  private final double rangeSquared;
  private final Predicate<PortalEndpoint> primaryVisible;
  private final List<View> views = new ArrayList<>();
  private final CameraRenderState[] cameras =
      new CameraRenderState[PortalSceneManager.RECURSION_DEPTH - 1];

  private PortalScenePlanner(
      ResourceKey<Level> dimension, CameraRenderState root, Matrix4fc projection,
      Matrix4fc modelView, List<PortalLink> links, double range,
      Predicate<PortalEndpoint> primaryVisible) {
    this.links = links.stream().filter(link ->
        link.entrance().endpoint().dimension().equals(dimension)
            && link.exit().endpoint().dimension().equals(dimension)).toList();
    this.projection = projection;
    viewEffects = new Matrix4f(modelView).rotate(root.orientation);
    rangeSquared = range * range;
    this.primaryVisible = primaryVisible;
  }

  static List<View> plan(
      ResourceKey<Level> dimension, CameraRenderState root, Matrix4fc projection,
      Matrix4fc modelView, List<PortalLink> links, double range,
      Predicate<PortalEndpoint> primaryVisible) {
    var planner = new PortalScenePlanner(
        dimension, root, projection, modelView, links, range, primaryVisible);
    planner.append(List.of(), root, modelView, PortalScreenVisibility.SCREEN);
    return planner.views;
  }

  private void append(
      List<PortalLink> path, CameraRenderState parent, Matrix4fc modelView,
      PortalScreenVisibility.Bounds parentBounds) {
    for (var link : links) {
      var entrance = link.entrance().endpoint();
      if (!path.isEmpty() && path.getLast().exit().equals(link.entrance())) continue;
      if (parent.pos.distanceToSqr(entrance.center()) > rangeSquared) continue;
      var bounds = PortalScreenVisibility.project(
          entrance, parent, projection, modelView, parentBounds);
      if (bounds != null && path.isEmpty() && !primaryVisible.test(entrance)) bounds = null;
      if (!path.isEmpty() && bounds == null) continue;
      var child = new ArrayList<>(path);
      child.add(link);
      var childPath = List.copyOf(child);
      var transform = link.transform();
      var position = transform.point(parent.pos);
      var orientation = transform.rotation().mul(parent.orientation);
      if (bounds != null && childPath.size() < PortalSceneManager.RECURSION_DEPTH) {
        var next = cameras[path.size()];
        if (next == null) cameras[path.size()] = next = new CameraRenderState();
        next.pos = position;
        next.orientation.set(orientation);
        next.viewRotationMatrix.rotation(new Quaternionf(orientation).conjugate()).mulLocal(viewEffects);
        append(childPath, next, next.viewRotationMatrix, bounds);
      }
      views.add(new View(childPath, bounds, position, orientation));
    }
  }

  record View(
      List<PortalLink> path, PortalScreenVisibility.Bounds bounds, Vec3 position,
      Quaternionf orientation) {}
}
