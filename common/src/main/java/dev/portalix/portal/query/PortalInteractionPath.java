package dev.portalix.portal.query;

import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalRoutes;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.camera.PortalEyePose;
import dev.portalix.portal.traversal.PortalEntityMovement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
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
    List<Path> frontier = List.of(new Path(eye, List.of()));
    Path selected = frontier.getFirst();
    double nearest = candidate(target, maximum, eye, selected);
    for (int depth = 0; depth < 2; depth++) {
      var next = new ArrayList<Path>();
      for (var path : frontier) {
        for (var link : PortalTopology.links(player.level())) {
          if (!PortalEntityMovement.canTraverse(player, link)) continue;
          if (!path.links.isEmpty() && path.links.getLast().inverse().equals(link)) continue;
          Vec3 virtualEye = link.transform().point(path.eye);
          var route = new ArrayList<>(path.links);
          route.add(link);
          var candidate = new Path(virtualEye, List.copyOf(route));
          next.add(candidate);
          double distance = candidate(target, maximum, eye, candidate);
          if (distance < nearest) {
            nearest = distance;
            selected = candidate;
          }
        }
      }
      frontier = next;
    }
    if (!Double.isFinite(nearest)) return Optional.empty();
    var route = new ArrayList<>(pose.eyePath());
    for (var link : selected.links) PortalRoutes.append(route, link);
    return Optional.of(new Route(List.copyOf(route), nearest, eye, selected.links));
  }

  private static double candidate(AABB target, double maximum, Vec3 eye, Path path) {
    var visible = PortalInteractionGeometry.closest(eye, target, path.links);
    if (visible.isEmpty()) return Double.POSITIVE_INFINITY;
    Vec3 closest = visible.orElseThrow();
    double distance = path.eye.distanceTo(closest);
    return distance <= maximum ? distance : Double.POSITIVE_INFINITY;
  }

  private record Path(Vec3 eye, List<PortalLink> links) {}

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
