package dev.portalix.portal.traversal;

import dev.portalix.portal.PortalLink;
import dev.portalix.portal.camera.PortalLookAngles;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.OldMinecartBehavior;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PortalPassengerGroup {
  private PortalPassengerGroup() {}

  public static List<AABB> hulls(Entity root) {
    if (root.getPassengers().isEmpty()) return List.of(collisionHull(root));
    var hulls = new ArrayList<AABB>();
    appendHulls(root, hulls);
    return hulls;
  }

  private static void appendHulls(Entity entity, List<AABB> hulls) {
    hulls.add(collisionHull(entity));
    for (var passenger : entity.getPassengers()) appendHulls(passenger, hulls);
  }

  private static AABB collisionHull(Entity entity) {
    var hull = entity.getBoundingBox();
    if (entity instanceof AbstractMinecart cart && cart.isOnRails()
        && cart.getBehavior() instanceof OldMinecartBehavior behavior) {
      var track = behavior.getPos(cart.getX(), cart.getY(), cart.getZ());
      if (track != null) return hull.move(0, track.y - cart.getY(), 0);
    }
    if (entity.getVehicle() instanceof AbstractMinecart cart && cart.isOnRails()) {
      // Seated passengers extend into the track bed; vanilla moves them with the cart.
      double bottom = Math.min(hull.maxY, Math.max(hull.minY, collisionHull(cart).minY));
      return new AABB(hull.minX, bottom, hull.minZ, hull.maxX, hull.maxY, hull.maxZ);
    }
    return hull;
  }

  public static AABB bounds(Entity root) {
    var bounds = collisionHull(root);
    for (var passenger : root.getPassengers()) bounds = bounds.minmax(bounds(passenger));
    return bounds;
  }

  public static AABB bounds(List<AABB> hulls) {
    AABB bounds = hulls.getFirst();
    for (int i = 1; i < hulls.size(); i++) bounds = bounds.minmax(hulls.get(i));
    return bounds;
  }

  public static void positionPassengers(Entity root) {
    for (Entity passenger : root.getPassengers()) {
      root.positionRider(passenger);
      positionPassengers(passenger);
    }
  }

  public static List<Arrival> arrival(Entity root, PortalLink link, Vec3 rootFeet) {
    var result = new ArrayList<Arrival>();
    arrival(root, link, rootFeet, result);
    return List.copyOf(result);
  }

  private static void arrival(Entity entity, PortalLink link, Vec3 feet, List<Arrival> result) {
    var angles =
        PortalEntityAngles.transform(entity, link.transform(), entity.getYRot(), entity.getXRot());
    float yaw = angles.yaw(), pitch = angles.pitch();
    result.add(
        new Arrival(
            entity,
            entity.position(),
            feet,
            yaw,
            pitch,
            collisionHull(entity).move(feet.subtract(entity.position())),
            new Vec3(entity.xo, entity.yo, entity.zo)));
    for (Entity passenger : entity.getPassengers()) {
      Vec3 seat = entity.getPassengerRidingPosition(passenger).subtract(entity.position());
      seat = seat.yRot((float) Math.toRadians(entity.getYRot() - yaw));
      float passengerYaw =
          PortalLookAngles.transform(link.transform(), passenger.getYRot(), passenger.getXRot())
              .yaw();
      Vec3 attachment =
          passenger
              .getVehicleAttachmentPoint(entity)
              .yRot((float) Math.toRadians(passenger.getYRot() - passengerYaw));
      arrival(passenger, link, feet.add(seat).subtract(attachment), result);
    }
  }

  public record Arrival(
      Entity entity,
      Vec3 before,
      Vec3 after,
      float yaw,
      float pitch,
      AABB hull,
      Vec3 previousFeet) {}
}
