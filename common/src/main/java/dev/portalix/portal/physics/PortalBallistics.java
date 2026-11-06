package dev.portalix.portal.physics;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.geometry.PortalAperture;
import java.util.OptionalDouble;
import net.minecraft.world.phys.Vec3;

public final class PortalBallistics {
  private PortalBallistics() {}

  public static Vec3 velocity(Vec3 tickVelocity, double gravity, double time) {
    return tickVelocity.add(0, gravity * (.5 - time), 0);
  }

  public static Vec3 displacement(Vec3 tickVelocity, double gravity, double time, double duration) {
    return velocity(tickVelocity, gravity, time)
        .scale(duration)
        .add(0, -gravity * duration * duration / 2, 0);
  }

  public static OptionalDouble crossing(
      PortalEndpoint portal, Vec3 center, Vec3 tickVelocity, double gravity, double time) {
    double distance = PortalAperture.signedDistance(portal, center);
    if (distance < -1e-5) return OptionalDouble.empty();
    distance = Math.max(0, distance);
    Vec3 velocity = velocity(tickVelocity, gravity, time);
    double speed = velocity.dot(portal.normal());
    double acceleration = -gravity * portal.normal().y;
    double duration;
    if (Math.abs(acceleration) < 1e-12) {
      if (speed >= 0) return OptionalDouble.empty();
      duration = -distance / speed;
    } else {
      double discriminant = speed * speed - 2 * acceleration * distance;
      if (discriminant < 0) return OptionalDouble.empty();
      double inwardSpeed = -Math.sqrt(discriminant);
      duration = (inwardSpeed - speed) / acceleration;
    }
    if (duration < 0 || duration > 1 - time) return OptionalDouble.empty();
    return OptionalDouble.of(duration);
  }
}
