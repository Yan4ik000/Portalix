package dev.portalix.carry;

import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalLinkId;
import dev.portalix.portal.PortalRoutes;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.camera.PortalEyePose;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.query.PortalRaycast;
import dev.portalix.portal.traversal.PortalEntityMovement;
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
  public static final double DISTANCE = 2.125;
  public static final double SWEEP_MARGIN = 0.01;

  private CarryAnchor() {}

  public static Vec3 holdPosition(Vec3 eye, Vec3 look) {
    return eye.add(look.scale(DISTANCE));
  }

  public static Vec3 playerHoldPosition(Player player) {
    var eye = PortalEyePose.sample(player);
    Vec3 target = holdPosition(eye.position(), eye.forward());
    for (var link : eye.eyePath().reversed()) target = link.transform().inverse().point(target);
    return target;
  }

  public static List<PortalLink> resolveRoute(Entity entity, List<PortalLinkId> references) {
    var available = PortalTopology.links(entity.level());
    var links = new ArrayList<PortalLink>(references.size());
    for (var reference : references) {
      var link = reference.resolve(available);
      if (link == null) return null;
      links.add(link);
    }
    return links;
  }

  public static Vec3 routeHoldPosition(Player player, List<PortalLink> route) {
    Vec3 target = playerHoldPosition(player);
    for (var link : route) target = link.transform().point(target);
    return target;
  }

  public static Vec3 routeHoldPosition(Player player, List<PortalLink> route, AABB hull) {
    Vec3 desired = routeHoldPosition(player, route);
    var eye = PortalEyePose.sample(player);
    var aim =
        PortalRaycast.cast(
            eye.position(),
            eye.forward().scale(DISTANCE),
            PortalTopology.links(player.level()),
            link -> PortalEntityMovement.canTraverse(player, link),
            (from, to) -> BlockHitResult.miss(to, Direction.NORTH, BlockPos.containing(to)));
    var travel = new ArrayList<PortalLinkId>();
    for (var link : route.reversed()) travel.add(PortalLinkId.fromLink(link.inverse()));
    for (var link : eye.eyePath()) travel.add(PortalLinkId.fromLink(link));
    for (var link : aim.links()) travel.add(PortalLinkId.fromLink(link));
    var path = PortalRoutes.simplifyIds(travel);
    if (path.isEmpty()) return desired;
    var entrance =
        path.getFirst().resolve(PortalTopology.links(player.level())).entrance().endpoint();
    Vec3 seam = PortalAperture.shortestPathPoint(entrance, hull.getCenter(), desired);
    var fitted = PortalAperture.fitHull(entrance, hull, seam);
    return fitted.isEmpty()
        ? desired
        : fitted.orElseThrow().subtract(entrance.normal().scale(SWEEP_MARGIN));
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
    var eyeRoute = new ArrayList<>(references);
    for (var link : eye.eyePath()) eyeRoute.addFirst(PortalLinkId.fromLink(link.inverse()));
    List<PortalLink> route = resolveRoute(holder, PortalRoutes.simplifyIds(eyeRoute));
    if (route == null) return Double.POSITIVE_INFINITY;
    if (route.stream().anyMatch(link -> !PortalEntityMovement.canTraverse(holder, link)))
      return Double.POSITIVE_INFINITY;
    return PortalRoutes.distance(eye.position(), target, route);
  }
}
