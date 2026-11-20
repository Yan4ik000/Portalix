package dev.portalix.portal.physics;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.geometry.PortalTransform;
import dev.portalix.resource.SourceUnits;
import java.util.OptionalDouble;
import net.minecraft.world.phys.Vec3;

public final class PortalExitVelocity {
  public static final double FLOOR_NORMAL = 0.8660253882408142;
  private static final double SPEED_UNIT = SourceUnits.SOURCE_SPEED_BLOCKS_PER_TICK;
  private static final double PLAYER_FLOOR_SPEED = 300 * SPEED_UNIT;
  private static final double PLAYER_FLOOR_RISE =
      300.0 * 300 / (2 * 600) * SourceUnits.SOURCE_UNIT_BLOCKS;

  private PortalExitVelocity() {}

  public static boolean isFloor(PortalEndpoint endpoint) {
    return endpoint.normal().y > FLOOR_NORMAL;
  }

  public static OptionalDouble minimumExitSpeed(
      boolean player,
      PortalEndpoint entrance,
      PortalEndpoint exit,
      Vec3 exitHullCenter,
      double hullHeight,
      double gravity,
      double airDrag) {
    if (isFloor(exit)) {
      return OptionalDouble.of(
          player
              ? playerFloorExitSpeed(gravity, airDrag)
              : (isFloor(entrance) ? 225 : 50) * SPEED_UNIT);
    }
    double normalY = exit.normal().y;
    if (!player || normalY <= .5 || gravity == 0) return OptionalDouble.empty();
    Vec3 foot = exitHullCenter.add(0, -hullHeight / 2, 0);
    Vec3 toLip = exit.center().subtract(exit.up().scale(exit.height() / 2)).subtract(foot);
    toLip = toLip.subtract(exit.right().scale(toLip.dot(exit.right())));
    double distance = Math.sqrt(toLip.x * toLip.x + toLip.z * toLip.z);
    double horizontalNormal =
        Math.sqrt(exit.normal().x * exit.normal().x + exit.normal().z * exit.normal().z);
    double a = -2 * normalY * (horizontalNormal * distance - normalY * toLip.y);
    double c = distance * distance * gravity;
    if (a >= 0 || c <= 0) return OptionalDouble.empty();
    return OptionalDouble.of(Math.min(Math.sqrt(-c / a), 300 * SPEED_UNIT));
  }

  public static Vec3 forPlayer(Vec3 velocity, Vec3 exitNormal, double minimumSpeed) {
    double outward = velocity.dot(exitNormal);
    return outward < minimumSpeed
        ? velocity.add(exitNormal.scale(minimumSpeed - outward))
        : velocity;
  }

  public static Vec3 forEntity(Vec3 velocity, Vec3 exitNormal, double minimumSpeed) {
    double speed = velocity.length();
    if (speed >= minimumSpeed) return velocity;
    return speed == 0 ? exitNormal.scale(minimumSpeed) : velocity.scale(minimumSpeed / speed);
  }

  public static Vec3 adjustedVelocity(
      PortalTransform transform,
      Vec3 incomingVelocity,
      Vec3 exitHullCenter,
      double hullHeight,
      double gravity) {
    Vec3 velocity = transform.vector(incomingVelocity);
    OptionalDouble minimum =
        minimumExitSpeed(
            false, transform.entrance(), transform.exit(), exitHullCenter, hullHeight, gravity, 1);
    if (minimum.isPresent())
      velocity = forEntity(velocity, transform.exit().normal(), minimum.getAsDouble());
    return velocity;
  }

  public static Vec3 toPlayerVelocity(
      PortalTransform transform,
      Vec3 incomingVelocity,
      Vec3 exitHullCenter,
      double hullHeight,
      double gravity,
      double airDrag) {
    OptionalDouble minimum =
        minimumExitSpeed(
            true,
            transform.entrance(),
            transform.exit(),
            exitHullCenter,
            hullHeight,
            gravity,
            airDrag);
    Vec3 velocity = transform.vector(incomingVelocity);
    return minimum.isPresent()
        ? forPlayer(velocity, transform.exit().normal(), minimum.getAsDouble())
        : velocity;
  }

  public static double playerFloorExitSpeed(double gravity, double airDrag) {
    if (gravity <= 0 || airDrag == 0) return PLAYER_FLOOR_SPEED;
    // Preserve the original minimum launch's rise using vanilla v[n+1] = (v[n] - g) * drag.
    double ticks = Math.ceil(Math.sqrt(2 * PLAYER_FLOOR_RISE / gravity));
    double sum;
    double gravityLoss;
    if (airDrag == 1) {
      sum = ticks;
      gravityLoss = gravity * ticks * (ticks + 1) / 2;
    } else {
      sum = airDrag * -Math.expm1(ticks * Math.log(airDrag)) / (1 - airDrag);
      gravityLoss = gravity * airDrag / (1 - airDrag) * (ticks - sum);
    }
    return Math.max(PLAYER_FLOOR_SPEED, (PLAYER_FLOOR_RISE + gravityLoss) / sum);
  }
}
