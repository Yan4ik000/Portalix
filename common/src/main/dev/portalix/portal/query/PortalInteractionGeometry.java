package dev.portalix.portal.query;

import dev.portalix.portal.PortalLink;
import dev.portalix.portal.geometry.ConvexPolyhedron;
import dev.portalix.portal.geometry.PortalAperture;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class PortalInteractionGeometry {
  private static final int MAX_ELLIPSE_CUTS = 20;
  private static final int MAX_OCCLUSION_RAYS = 64;
  private static final double APERTURE_INSET = 1e-7;

  private PortalInteractionGeometry() {}

  public static Optional<Vec3> closest(Vec3 eye, AABB target, List<PortalLink> route) {
    Vec3 virtualEye = eye;
    for (var link : route) virtualEye = link.transform().point(virtualEye);
    return closestFrom(virtualEye, target, route, Double.POSITIVE_INFINITY, null);
  }

  static Optional<Vec3> closestFrom(Vec3 virtualEye, AABB target, List<PortalLink> route,
      double reach, @Nullable Sightline sightline) {
    var visible = ConvexPolyhedron.box(target);
    List<EllipseGate> ellipses = null;
    Vec3 previousNormal = null;
    double previousDistance = 0;
    for (int gate = 0; gate < route.size(); gate++) {
      var link = route.get(gate);
      var portal = link.entrance().endpoint();
      var aperture = PortalAperture.limitingAperture(link);
      double inset = sightline != null && aperture.normal().y != 1 ? APERTURE_INSET : 0;
      Vec3 center = portal.center();
      Vec3 right = portal.right().scale(PortalAperture.passageHalfWidth(aperture) - inset);
      Vec3 up = portal.up().scale(PortalAperture.passageHalfHeight(aperture) - inset);
      Vec3 normal = portal.normal();
      for (int i = gate; i < route.size(); i++) {
        var transform = route.get(i).transform();
        center = transform.point(center);
        right = transform.vector(right);
        up = transform.vector(up);
        normal = transform.vector(normal);
      }
      Vec3 eyeOffset = virtualEye.subtract(center);
      double distance = eyeOffset.dot(normal);
      if (distance <= 0) return Optional.empty();
      if (previousNormal != null) {
        Vec3 order = previousNormal.scale(distance).subtract(normal.scale(previousDistance));
        if (order.lengthSqr() > 1e-20) {
          visible = visible.clip(ConvexPolyhedron.Plane.through(order, virtualEye));
        }
      }
      previousNormal = normal;
      previousDistance = distance;
      visible = visible.clip(new ConvexPolyhedron.Plane(normal, normal.dot(center)));
      if (visible.vertices().isEmpty()) return Optional.empty();
      if (aperture.normal().y == 1) {
        if (ellipses == null) ellipses = new ArrayList<>();
        ellipses.add(new EllipseGate(center, normal,
            right.scale(1 / right.lengthSqr()), up.scale(1 / up.lengthSqr()), distance));
      }
      for (var edge : List.of(right, right.scale(-1), up, up.scale(-1))) {
        visible = visible.clip(projectEdge(virtualEye, eyeOffset, normal, distance, edge, edge.lengthSqr()));
        if (visible.vertices().isEmpty()) return Optional.empty();
      }
    }

    double reachSquared = reach * reach;
    var nearest = nearest(visible, virtualEye, ellipses, reachSquared);
    if (nearest == null) return Optional.empty();
    if (sightline == null) return Optional.of(nearest.point());
    var cover = sightline.occlusion(nearest.point());
    if (cover == null) return Optional.of(nearest.point());
    var pending = new PriorityQueue<Candidate>(Comparator.comparingDouble(Candidate::distanceSquared));
    Candidate unresolved = null;
    for (int ray = 1; ray < MAX_OCCLUSION_RAYS; ray++) {
      var shadow = cover.shadow();
      if (shadow == null) {
        if (unresolved == null) unresolved = nearest;
      } else {
        // Partition the candidate volume into convex pieces outside the blocker shadow.
        var remainder = nearest.volume();
        for (var plane : shadow) {
          double tolerance = PortalAperture.PLANE_EPSILON + 8 * Math.ulp(plane.offset());
          var outside = new ConvexPolyhedron.Plane(plane.normal().scale(-1), -plane.offset() - tolerance);
          var next = nearest(remainder.clip(outside), virtualEye, ellipses, reachSquared);
          if (next != null) pending.add(next);
          remainder = remainder.clip(plane);
          if (remainder.vertices().isEmpty()) break;
        }
      }
      nearest = pending.poll();
      if (nearest == null) break;
      cover = sightline.occlusion(nearest.point());
      if (cover == null) return Optional.of(nearest.point());
    }
    if (unresolved == null) return Optional.empty();
    // A ray redirected by a different portal has no outline voxel to subtract.
    var vertices = unresolved.volume().vertices();
    Vec3 center = Vec3.ZERO;
    for (var vertex : vertices) center = center.add(vertex);
    center = center.scale(1.0 / vertices.size());
    Vec3 best = null;
    for (int i = 0; i <= vertices.size(); i++) {
      Vec3 point = i == vertices.size() ? center : vertices.get(i).lerp(center, 1.0 / 16);
      double distanceSquared = point.distanceToSqr(virtualEye);
      if (distanceSquared <= reachSquared && sightline.occlusion(point) == null) {
        best = point;
        reachSquared = distanceSquared;
      }
    }
    return Optional.ofNullable(best);
  }

  private static @Nullable Candidate nearest(ConvexPolyhedron visible, Vec3 eye,
      @Nullable List<EllipseGate> ellipses, double reachSquared) {
    for (int cut = 0; cut < MAX_ELLIPSE_CUTS; cut++) {
      var point = visible.closestPoint(eye).orElse(null);
      if (point == null) return null;
      double distanceSquared = point.distanceToSqr(eye);
      if (distanceSquared > reachSquared) return null;
      ConvexPolyhedron.Plane plane = null;
      if (ellipses != null)
        for (var gate : ellipses) {
          plane = gate.separate(eye, point);
          if (plane != null) break;
        }
      if (plane == null) return new Candidate(visible, point, distanceSquared);
      visible = visible.clip(plane);
    }
    return null;
  }

  @FunctionalInterface
  interface Sightline {
    @Nullable Occlusion occlusion(Vec3 target);
  }

  record Occlusion(@Nullable List<ConvexPolyhedron.Plane> shadow) {
    static final Occlusion UNRESOLVED = new Occlusion(null);
  }

  private record Candidate(ConvexPolyhedron volume, Vec3 point, double distanceSquared) {}

  private static ConvexPolyhedron.Plane projectEdge(
      Vec3 eye, Vec3 eyeOffset, Vec3 normal, double distance, Vec3 tangent, double limit) {
    Vec3 side = tangent.scale(distance).subtract(normal.scale(tangent.dot(eyeOffset) - limit));
    return ConvexPolyhedron.Plane.through(side, eye);
  }

  private record EllipseGate(Vec3 center, Vec3 normal, Vec3 right, Vec3 up, double distance) {
    ConvexPolyhedron.Plane separate(Vec3 eye, Vec3 target) {
      Vec3 eyeOffset = eye.subtract(center), targetOffset = target.subtract(center);
      double depth = Math.max(0, -targetOffset.dot(normal));
      double span = distance + depth;
      Vec3 onGate = eyeOffset.scale(depth / span).add(targetOffset.scale(distance / span));
      double x = onGate.dot(right), y = onGate.dot(up);
      double radius = Math.hypot(x, y);
      if (radius < 1 - 1e-8) return null;
      // Project the tangent of the aperture back through the eye to clip the target volume.
      Vec3 tangent = right.scale(x / radius).add(up.scale(y / radius));
      return projectEdge(eye, eyeOffset, normal, distance, tangent, 1 - 2e-7);
    }
  }
}
