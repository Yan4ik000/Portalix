package dev.portalix.server.carry;

import dev.portalix.api.CarryPermission;
import dev.portalix.carry.CarryBindings;
import dev.portalix.carry.CarryMotionPath;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.traversal.PortalEntityMovement;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class CarryMovement {
  private CarryMovement() {}

  static @Nullable CarryMotionPath apply(ServerPlayer player, Entity entity, CarryMotionPath movement, BooleanSupplier valid) {
    var nodes = movement.nodes();
    if (nodes.size() == 1) return permitted(player, entity, entity.position(), valid) ? movement : null;
    for (int index = 1; index < nodes.size(); index++) {
      var node = nodes.get(index);
      Vec3 start = entity.getBoundingBox().getCenter();
      Vec3 point = node.point();
      if (!permitted(player, entity, entity.position().add(point.subtract(start)), valid)) return null;
      if (node.crossing() != null) {
        var link = node.crossing();
        if (PortalEntityMovement.crossAtPlane(
                entity, link, link.entrance().endpoint().normal().scale(-1)) == null
            || entity.getBoundingBox().getCenter().distanceToSqr(point) > 1e-10) return null;
      } else if (!point.equals(start)) {
        PortalEntityMovement.moveWithinSpace(entity, MoverType.SELF, point.subtract(start));
        if (!entity.isAlive() || entity.level() != player.level()) return null;
        var actual = entity.getBoundingBox().getCenter();
        if (actual.distanceToSqr(point) > 1e-10) {
          var completed = new ArrayList<>(nodes.subList(0, index));
          completed.add(new CarryMotionPath.Node(actual, null));
          return CarryMotionPath.ofNodes(completed);
        }
      }
    }
    return movement;
  }

  private static boolean permitted(ServerPlayer player, Entity entity, Vec3 feet, BooleanSupplier valid) {
    if (!valid.getAsBoolean()) return false;
    var level = entity.level();
    var topology = PortalTopology.links(level);
    var ownerPosition = player.position();
    var body = entity.getBoundingBox();
    var route = CarryBindings.route(entity);
    return CarryPermission.EVENT.invoker().allow(player, entity, CarryPermission.Operation.MOVE, feet)
        && valid.getAsBoolean()
        && PortalTopology.links(level) == topology
        && player.position().equals(ownerPosition)
        && entity.getBoundingBox().equals(body)
        && CarryBindings.route(entity).equals(route);
  }
}
