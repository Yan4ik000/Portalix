package dev.portalix.client.portal.scene;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;

final class PortalScenePlanner {
  private final ResourceKey<Level> dimension;
  private final Function<Vec3, List<PortalLink>> links;
  private final Matrix4fc projection;
  private final Matrix4f viewEffects;
  private final double rangeSquared;
  private final Predicate<PortalEndpoint> primaryVisible;
  private final List<View> views = new ArrayList<>();
  private final CameraRenderState[] cameras =
      new CameraRenderState[PortalSceneManager.RECURSION_DEPTH - 1];

  private PortalScenePlanner(
      ResourceKey<Level> dimension, CameraRenderState root, Matrix4fc projection,
      Matrix4fc modelView, Function<Vec3, List<PortalLink>> links, double range,
      Predicate<PortalEndpoint> primaryVisible) {
    this.dimension = dimension;
    this.links = links;
    this.projection = projection;
    viewEffects = new Matrix4f(modelView).rotate(root.orientation);
    rangeSquared = range * range;
    this.primaryVisible = primaryVisible;
  }

  static List<View> plan(
      ResourceKey<Level> dimension, CameraRenderState root, Matrix4fc projection,
      Matrix4fc modelView, List<PortalLink> links, double range,
      Predicate<PortalEndpoint> primaryVisible) {
    return plan(dimension, root, projection, modelView, position -> links, range, primaryVisible);
  }

  static List<View> plan(
      ClientLevel level, CameraRenderState root, Matrix4fc projection, Matrix4fc modelView,
      double range, Predicate<PortalEndpoint> primaryVisible) {
    return plan(level.dimension(), root, projection, modelView, position -> PortalTopology.entrances(
        level, position.x - range, position.z - range, position.x + range, position.z + range),
        range, primaryVisible);
  }

  private static List<View> plan(
      ResourceKey<Level> dimension, CameraRenderState root, Matrix4fc projection,
      Matrix4fc modelView, Function<Vec3, List<PortalLink>> links, double range,
      Predicate<PortalEndpoint> primaryVisible) {
    var planner = new PortalScenePlanner(dimension, root, projection, modelView, links, range,
        primaryVisible);
    planner.append(List.of(), root, modelView, PortalScreenVisibility.SCREEN);
    return planner.views;
  }

  private void append(
      List<PortalLink> path, CameraRenderState parent, Matrix4fc modelView,
      PortalScreenVisibility.Bounds parentBounds) {
    var candidates = links.apply(parent.pos);
    if (candidates.isEmpty()) return;
    var viewProjection = new Matrix4f(projection).mul(modelView);
    for (var link : candidates) {
      var entrance = link.entrance().endpoint();
      if (!entrance.dimension().equals(dimension)
          || !link.exit().endpoint().dimension().equals(dimension)) continue;
      if (!path.isEmpty() && path.getLast().exit().equals(link.entrance())) continue;
      if (parent.pos.distanceToSqr(entrance.center()) > rangeSquared) continue;
      var bounds = PortalScreenVisibility.project(
          entrance, parent, viewProjection, parentBounds);
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
