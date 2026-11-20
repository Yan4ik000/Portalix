package dev.portalix.client.portal.scene;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.client.portal.clip.PortalCullPlane;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalViewLimits;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;

final class PortalScenePlanner {
  static final int SCREEN_AREA_LIMIT = 2;
  private static final double TRAVERSAL_VIEW_DISTANCE_SQUARED = 16;

  private final ResourceKey<Level> dimension;
  private final int previewRadius;
  private final Vec3 rootOrigin;
  private final boolean remoteCamera;
  private final BiFunction<Vec3, Double, List<PortalLink>> links;
  private final double range;
  private final Matrix4fc projection;
  private final Matrix4f viewEffects;
  private final double rangeSquared;
  private final Predicate<PortalEndpoint> primaryVisible;
  private final int width;
  private final int height;
  private int remainingViews;
  private long remainingPixels;
  private final LongOpenHashSet regions = new LongOpenHashSet();

  private PortalScenePlanner(
      ResourceKey<Level> dimension, CameraRenderState root, Matrix4fc projection,
      Matrix4fc modelView, BiFunction<Vec3, Double, List<PortalLink>> links, double range,
      Predicate<PortalEndpoint> primaryVisible, int width, int height, int maxViews, Vec3 rootOrigin, boolean remoteCamera) {
    this.dimension = dimension;
    previewRadius = PortalViewLimits.preview((int) Math.ceil(range / 16));
    this.remoteCamera = remoteCamera;
    this.rootOrigin = rootOrigin;
    if (remoteCamera) regions.add(chunk(rootOrigin));
    this.links = links;
    this.range = range;
    this.projection = projection;
    viewEffects = new Matrix4f(modelView).rotate(root.orientation);
    rangeSquared = range * range;
    this.primaryVisible = primaryVisible;
    this.width = width;
    this.height = height;
    remainingViews = maxViews;
    remainingPixels = (long) width * height * SCREEN_AREA_LIMIT;
  }

  static List<View> plan(
      ResourceKey<Level> dimension, CameraRenderState root, Matrix4fc projection,
      Matrix4fc modelView, BiFunction<Vec3, Double, List<PortalLink>> links, double range,
      Predicate<PortalEndpoint> primaryVisible, int width, int height, int maxViews,
      Vec3 rootOrigin, boolean remoteCamera) {
    return new PortalScenePlanner(dimension, root, projection, modelView, links, range,
        primaryVisible, width, height, maxViews, rootOrigin, remoteCamera).plan(root, modelView);
  }

  private static long chunk(Vec3 point) {
    return ChunkPos.pack((int) Math.floor(point.x) >> 4, (int) Math.floor(point.z) >> 4);
  }

  private List<View> plan(CameraRenderState root, Matrix4fc modelView) {
    var views = new ArrayList<View>();
    var roots = new ArrayList<Candidate>();
    collectCandidates(List.of(), root, modelView, PortalScreenVisibility.SCREEN, roots, views);
    var parents = selectViews(roots, views);
    if (parents.isEmpty() || remainingViews == 0) return views;
    var camera = new CameraRenderState();
    for (int depth = 1; depth < PortalViewLimits.DEPTH && remainingViews > 0 && !parents.isEmpty(); depth++) {
      var candidates = new ArrayList<Candidate>();
      for (var parent : parents) {
        camera.pos = parent.position();
        camera.orientation.set(parent.orientation());
        camera.viewRotationMatrix.rotation(new Quaternionf(parent.orientation()).conjugate()).mulLocal(viewEffects);
        collectCandidates(parent.path(), camera, camera.viewRotationMatrix, parent.bounds(), candidates, views);
      }
      parents = selectViews(candidates, views);
    }
    return views;
  }

  private List<View> selectViews(List<Candidate> candidates, List<View> renderOrder) {
    candidates.sort(Comparator.comparing(Candidate::nearby).reversed()
        .thenComparing(Comparator.comparingLong(Candidate::pixels).reversed())
        .thenComparingDouble(Candidate::distanceSquared));
    var selected = new ArrayList<View>();
    for (var candidate : candidates) {
      if (remainingViews == 0) break;
      if (candidate.pixels() > remainingPixels) continue;
      var view = candidate.view();
      long region = chunk(PortalViewLimits.origin(view.link().exit().endpoint()));
      if (regions.size() == PortalViewLimits.REGIONS && !regions.contains(region)) continue;
      regions.add(region);
      selected.add(view);
      remainingPixels -= candidate.pixels();
      remainingViews--;
    }
    renderOrder.addAll(0, selected);
    return selected;
  }

  private void collectCandidates(
      List<PortalLink> path, CameraRenderState parent, Matrix4fc modelView,
      PortalScreenVisibility.Bounds parentBounds, List<Candidate> visible, List<View> hidden) {
    var origin = path.isEmpty() ? rootOrigin : PortalViewLimits.origin(path.getLast().exit().endpoint());
    boolean primaryRange = path.isEmpty() && !remoteCamera;
    var candidates = links.apply(origin, primaryRange ? range : previewRadius * 16.0);
    if (candidates.isEmpty()) return;
    var exitPlane = path.isEmpty() ? null : PortalCullPlane.of(path.getLast().exit().endpoint());
    var viewProjection = new Matrix4f(projection).mul(modelView);
    for (var link : candidates) {
      var entrance = link.entrance().endpoint();
      if (!entrance.dimension().equals(dimension)
          || !link.exit().endpoint().dimension().equals(dimension)) continue;
      if (!path.isEmpty() && path.getLast().exit().equals(link.entrance())) continue;
      double distanceSquared = parent.pos.distanceToSqr(entrance.center());
      if (primaryRange) {
        if (distanceSquared > rangeSquared) continue;
      } else if (!PortalViewLimits.containsEntrance(origin, entrance, previewRadius)) continue;
      var bounds = PortalScreenVisibility.project(entrance, parent, viewProjection, parentBounds, exitPlane);
      if (bounds != null && path.isEmpty() && !primaryVisible.test(entrance)) bounds = null;
      if (!path.isEmpty() && bounds == null) continue;
      var child = new ArrayList<>(path);
      child.add(link);
      var transform = link.transform();
      var view = new View(List.copyOf(child), bounds, transform.point(parent.pos),
          transform.rotation().mul(parent.orientation));
      if (bounds == null) {
        hidden.add(view);
      } else {
        var viewport = PortalSceneViewport.of(bounds, width, height);
        boolean nearby = path.isEmpty() && distanceSquared <= TRAVERSAL_VIEW_DISTANCE_SQUARED;
        visible.add(new Candidate(view, viewport.pixels(), distanceSquared, nearby));
      }
    }
  }

  private record Candidate(View view, long pixels, double distanceSquared, boolean nearby) {}

  record View(
      List<PortalLink> path, PortalScreenVisibility.Bounds bounds, Vec3 position,
      Quaternionf orientation) {
    PortalLink link() {
      return path.getLast();
    }
  }
}
