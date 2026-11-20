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
    if (root.getPassengers().isEmpty()) return List.of(collisionHull(root, root.position(), null));
    var hulls = new ArrayList<AABB>();
    appendHulls(root, root.position(), null, hulls);
    return hulls;
  }

  private static void appendHulls(Entity entity, Vec3 feet, AABB vehicleHull, List<AABB> hulls) {
    var hull = collisionHull(entity, feet, vehicleHull);
    hulls.add(hull);
    for (var passenger : entity.getPassengers()) {
      // Native vehicle packets can move the vehicle before the next passenger tick.
      Vec3 seat = entity.getPassengerRidingPosition(passenger).subtract(entity.position());
      Vec3 passengerFeet = feet.add(seat).subtract(passenger.getVehicleAttachmentPoint(entity));
      appendHulls(passenger, passengerFeet, hull, hulls);
    }
  }

  private static AABB collisionHull(Entity entity, Vec3 feet, AABB vehicleHull) {
    var hull = entity.getBoundingBox();
    if (!feet.equals(entity.position())) hull = hull.move(feet.subtract(entity.position()));
    if (entity instanceof AbstractMinecart cart && cart.isOnRails()
        && cart.getBehavior() instanceof OldMinecartBehavior behavior) {
      var track = behavior.getPos(cart.getX(), cart.getY(), cart.getZ());
      if (track != null) return hull.move(0, track.y - cart.getY(), 0);
    }
    if (entity.getVehicle() instanceof AbstractMinecart cart && cart.isOnRails()) {
      // Seated passengers extend into the track bed; vanilla moves them with the cart.
      var trackHull = vehicleHull != null ? vehicleHull
          : collisionHull(cart, cart.position().add(feet.subtract(entity.position())), null);
      double bottom = Math.min(hull.maxY, Math.max(hull.minY, trackHull.minY));
      return new AABB(hull.minX, bottom, hull.minZ, hull.maxX, hull.maxY, hull.maxZ);
    }
    return hull;
  }

  public static AABB bounds(Entity root) {
    if (root.getPassengers().isEmpty()) return collisionHull(root, root.position(), null);
    return bounds(hulls(root));
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
    arrival(root, link, rootFeet, null, result);
    return List.copyOf(result);
  }

  private static void arrival(
      Entity entity, PortalLink link, Vec3 feet, AABB vehicleHull, List<Arrival> result) {
    var angles =
        PortalEntityAngles.transform(entity, link.transform(), entity.getYRot(), entity.getXRot());
    float yaw = angles.yaw(), pitch = angles.pitch();
    var hull = collisionHull(entity, feet, vehicleHull);
    result.add(
        new Arrival(
            entity,
            entity.position(),
            feet,
            yaw,
            pitch,
            hull,
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
      arrival(passenger, link, feet.add(seat).subtract(attachment), hull, result);
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
