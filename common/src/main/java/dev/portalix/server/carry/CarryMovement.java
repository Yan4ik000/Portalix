package dev.portalix.server.carry;

import dev.portalix.api.CarryPermission;
import dev.portalix.carry.CarryAnchor;
import dev.portalix.carry.CarryBindings;
import dev.portalix.carry.CarryMotionPath;
import dev.portalix.carry.EntityCarryWorld;
import dev.portalix.network.carry.CarryInputPayload;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalRoutes;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.traversal.PortalEntityMovement;
import java.util.ArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

final class CarryMovement {
  private CarryMovement() {}

  static CarryMotionPath validate(
      ServerPlayer player, Entity entity, CarryInputPayload input, double maxHolderDistance) {
    double diameter = maxHolderDistance * 2;
    Vec3 previous = entity.getBoundingBox().getCenter();
    double distance = 0;
    var route = new ArrayList<>(CarryBindings.route(entity));
    var links = PortalTopology.links(entity.level());
    var nodes = new ArrayList<CarryMotionPath.Node>(input.nodes().size());
    for (var node : input.nodes()) {
      Vec3 point = node.point();
      if (!finite(point)) return null;
      PortalLink link = null;
      if (node.crossing() != null) {
        link = node.crossing().resolve(links);
        if (link == null
            || !PortalEntityMovement.canTraverse(player, link)
            || Math.abs(PortalAperture.signedDistance(link.entrance().endpoint(), previous)) > 1e-5
            || !PortalAperture.contains(link.entrance().endpoint(), previous)
            || link.transform().point(previous).distanceToSqr(point) > 1e-10) return null;
        route.add(node.crossing());
        route = new ArrayList<>(PortalRoutes.simplifyIds(route));
      } else distance += previous.distanceTo(point);
      if (distance > diameter
          || CarryAnchor.routeDistance(player, point, route) > maxHolderDistance) return null;
      nodes.add(new CarryMotionPath.Node(point, link));
      previous = point;
    }
    return CarryMotionPath.ofNodes(nodes);
  }

  static boolean apply(
      ServerPlayer player, Entity entity, EntityCarryWorld collisions, CarryMotionPath movement) {
    for (var node : movement.nodes()) {
      Vec3 start = entity.getBoundingBox().getCenter();
      Vec3 point = node.point();
      if (!CarryPermission.EVENT
          .invoker()
          .allow(
              player,
              entity,
              CarryPermission.Operation.MOVE,
              point.subtract(start.subtract(entity.position())))) return false;
      if (node.crossing() != null) {
        var link = node.crossing();
        if (PortalEntityMovement.crossAtPlane(
                entity, link, link.entrance().endpoint().normal().scale(-1))
            == null) return false;
      } else {
        var swept = collisions.sweepWithinSpace(start, point);
        if (swept.path().nodes().stream().anyMatch(n -> n.crossing() != null)
            || swept.path().end().distanceToSqr(point) > 1e-10) return false;
        PortalEntityMovement.moveWithinSpace(entity, MoverType.SELF, point.subtract(start));
      }
      if (entity.getBoundingBox().getCenter().distanceToSqr(point) > 1e-10) return false;
    }
    return true;
  }

  private static boolean finite(Vec3 point) {
    return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
  }
}
