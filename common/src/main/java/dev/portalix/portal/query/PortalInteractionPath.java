package dev.portalix.portal.query;

import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalRoutes;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.camera.PortalEyePose;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.traversal.PortalEntityMovement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PortalInteractionPath {
  private PortalInteractionPath() {}

  public static double distance(Player player, AABB target, double maximum) {
    return closest(player, target, maximum).map(Route::distance).orElse(Double.POSITIVE_INFINITY);
  }

  public static Optional<Route> closest(Player player, AABB target, double maximum) {
    var pose = PortalEyePose.sample(player);
    Vec3 eye = pose.position();
    var bounds = target.inflate(PortalAperture.PLANE_EPSILON);
    List<PortalLink> selected = List.of();
    double nearest = candidate(target, bounds, maximum, eye, selected);
    Level level = player.level();
    var firstLinks = entrances(level, eye, Math.min(maximum, nearest));
    if (!firstLinks.isEmpty()) {
      var firstSteps = new ArrayList<Approach>();
      var readiness = new HashMap<PortalLink, Boolean>();
      for (var link : firstLinks) {
        double limit = Math.min(maximum, nearest);
        if (!reachable(eye, link, limit)
            || !readiness.computeIfAbsent(link, candidate -> PortalEntityMovement.canTraverse(player, candidate)))
          continue;
        Vec3 virtualEye = link.transform().point(eye);
        firstSteps.add(new Approach(link, virtualEye));
        if (bounds.distanceToSqr(virtualEye) > limit * limit) continue;
        var route = List.of(link);
        double distance = candidate(target, bounds, limit, virtualEye, route);
        if (distance < nearest) {
          nearest = distance;
          selected = route;
        }
      }
      for (var first : firstSteps) {
        if (!reachable(eye, first.link(), Math.min(maximum, nearest))) continue;
        var inverse = first.link().inverse();
        for (var link : entrances(level, first.eye(), Math.min(maximum, nearest))) {
          double limit = Math.min(maximum, nearest);
          if (inverse.equals(link) || !reachable(first.eye(), link, limit)) continue;
          Vec3 virtualEye = link.transform().point(first.eye());
          if (bounds.distanceToSqr(virtualEye) > limit * limit
              || !readiness.computeIfAbsent(link, candidate -> PortalEntityMovement.canTraverse(player, candidate)))
            continue;
          var route = List.of(first.link(), link);
          double distance = candidate(target, bounds, limit, virtualEye, route);
          if (distance < nearest) {
            nearest = distance;
            selected = route;
          }
        }
      }
    }
    if (!Double.isFinite(nearest)) return Optional.empty();
    var route = new ArrayList<>(pose.eyePath());
    for (var link : selected) PortalRoutes.append(route, link);
    return Optional.of(new Route(List.copyOf(route), nearest, eye, selected));
  }

  private static List<PortalLink> entrances(Level level, Vec3 eye, double maximum) {
    if (!(maximum >= 0)) return List.of();
    double reach = maximum + PortalAperture.MAX_APERTURE_EXTENT + PortalAperture.PLANE_EPSILON;
    return PortalTopology.entrances(level, eye.x - reach, eye.z - reach, eye.x + reach, eye.z + reach);
  }

  private static boolean reachable(Vec3 eye, PortalLink link, double maximum) {
    var entrance = link.entrance().endpoint();
    double reach = maximum + PortalAperture.MAX_APERTURE_EXTENT + PortalAperture.PLANE_EPSILON;
    return entrance.center().distanceToSqr(eye) <= reach * reach
        && PortalAperture.signedDistance(entrance, eye) >= -PortalAperture.PLANE_EPSILON;
  }

  private static double candidate(
      AABB target, AABB bounds, double maximum, Vec3 eye, List<PortalLink> route) {
    if (!(maximum >= 0) || bounds.distanceToSqr(eye) > maximum * maximum)
      return Double.POSITIVE_INFINITY;
    var visible = PortalInteractionGeometry.closestFrom(eye, target, route);
    if (visible.isEmpty()) return Double.POSITIVE_INFINITY;
    Vec3 closest = visible.orElseThrow();
    double distance = eye.distanceTo(closest);
    return distance <= maximum ? distance : Double.POSITIVE_INFINITY;
  }

  private record Approach(PortalLink link, Vec3 eye) {}

  public record Route(List<PortalLink> links, double distance, Vec3 eye, List<PortalLink> sightline) {
    public Vec3 point(Vec3 point) {
      for (var link : links) point = link.transform().point(point);
      return point;
    }

    public boolean visible(AABB target) {
      return PortalInteractionGeometry.closest(eye, target, sightline).isPresent();
    }
  }
}
