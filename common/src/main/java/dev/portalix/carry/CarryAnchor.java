package dev.portalix.carry;

import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalLinkId;
import dev.portalix.portal.PortalRoutes;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.camera.PortalEyePose;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.query.PortalRaycast;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class CarryAnchor {
  public static final double MAX_HOLDER_DISTANCE = 5.0;
  public static final double DISTANCE = 2.125;
  public static final double SWEEP_MARGIN = 0.01;

  private CarryAnchor() {}

  public static Vec3 holdPosition(Vec3 eye, Vec3 look) {
    return eye.add(look.scale(DISTANCE));
  }

  private static Vec3 playerHoldPosition(PortalEyePose.Pose eye) {
    Vec3 target = holdPosition(eye.position(), eye.forward());
    for (var link : eye.eyePath().reversed()) target = link.transform().inverse().point(target);
    return target;
  }

  public static List<PortalLink> resolveRoute(Entity entity, List<PortalLinkId> references) {
    if (references.isEmpty()) return List.of();
    var links = new ArrayList<PortalLink>(references.size());
    for (var reference : references) {
      var link = PortalTopology.resolve(entity.level(), reference);
      if (link == null) return null;
      links.add(link);
    }
    return links;
  }

  public static Vec3 routeHoldPosition(Player player, List<PortalLink> route, AABB hull) {
    return holdTarget(player).position(route, hull);
  }

  public static HoldTarget holdTarget(Player player) {
    var eye = PortalEyePose.sample(player);
    Vec3 desired = playerHoldPosition(eye);
    if (PortalTopology.links(player.level()).isEmpty()) return new HoldTarget(desired, List.of());
    var aim =
        PortalRaycast.cast(
            player, eye.position(), eye.forward().scale(DISTANCE),
            (from, to) -> BlockHitResult.miss(to, Direction.NORTH, BlockPos.containing(to)));
    var travel = new ArrayList<>(eye.eyePath());
    travel.addAll(aim.links());
    return new HoldTarget(desired, PortalRoutes.simplify(travel));
  }

  public record HoldTarget(Vec3 desired, List<PortalLink> travel) {
    public Vec3 position(List<PortalLink> route, AABB hull) {
      Vec3 local = desired;
      for (var link : route) local = link.transform().point(local);
      if (route.isEmpty() && travel.isEmpty()) return local;
      var path = new ArrayList<PortalLink>(route.size() + travel.size());
      for (var link : route.reversed()) path.add(link.inverse());
      path.addAll(travel);
      var simplified = PortalRoutes.simplify(path);
      if (simplified.isEmpty()) return local;
      var entrance = simplified.getFirst().entrance().endpoint();
      Vec3 seam = PortalAperture.shortestPathPoint(entrance, hull.getCenter(), local);
      return PortalAperture.fitHull(entrance, hull, seam)
          .map(point -> point.subtract(entrance.normal().scale(SWEEP_MARGIN)))
          .orElse(local);
    }
  }

  public static Vec3 routeLookDirection(Player player, List<PortalLink> route) {
    var eye = PortalEyePose.sample(player);
    Vec3 look = eye.forward();
    for (var link : eye.eyePath().reversed()) look = link.transform().inverse().vector(look);
    for (var link : route) look = link.transform().vector(look);
    return look;
  }

  public static double routeDistance(Player holder, Vec3 target, List<PortalLinkId> references) {
    var eye = PortalEyePose.sample(holder);
    var route = distanceRoute(holder, eye, references);
    return route == null ? Double.POSITIVE_INFINITY : PortalRoutes.distance(eye.position(), target, route);
  }

  public static List<PortalLink> distanceRoute(
      Player holder, PortalEyePose.Pose eye, List<PortalLinkId> references) {
    if (eye.eyePath().isEmpty() && references.isEmpty()) return List.of();
    var eyeRoute = new ArrayList<>(references);
    for (var link : eye.eyePath()) eyeRoute.addFirst(PortalLinkId.fromLink(link.inverse()));
    return resolveRoute(holder, PortalRoutes.simplifyIds(eyeRoute));
  }
}
