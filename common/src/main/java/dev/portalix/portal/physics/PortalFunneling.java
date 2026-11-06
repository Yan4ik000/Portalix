package dev.portalix.portal.physics;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.resource.SourceUnits;
import net.minecraft.world.phys.Vec3;

public final class PortalFunneling {
  private static final double SOURCE_UNIT = SourceUnits.SOURCE_UNIT_BLOCKS;
  private static final double VELOCITY_UNIT = SourceUnits.SOURCE_SPEED_BLOCKS_PER_TICK;

  private PortalFunneling() {}

  public static boolean eligible(PortalEndpoint portal, Vec3 center, Vec3 velocity) {
    double horizontal = velocity.horizontalDistanceSqr() / (VELOCITY_UNIT * VELOCITY_UNIT);
    if (Math.abs(velocity.y) < VELOCITY_UNIT || horizontal >= 4096 || portal.normal().y <= .8f)
      return false;
    Vec3 delta = portal.center().subtract(center);
    if (delta.y < -1024 * SOURCE_UNIT || delta.y >= 0) return false;
    Vec3 right = horizontal(portal.up().cross(portal.normal()));
    Vec3 up = horizontal(portal.up());
    double halfWidth = portal.width() * .75;
    double halfHeight = portal.height() * .75;
    return right.scale(delta.dot(right)).lengthSqr() <= halfWidth * halfWidth
        && up.scale(delta.dot(up)).lengthSqr() <= halfHeight * halfHeight;
  }

  public static Vec3 velocity(PortalEndpoint portal, Vec3 center, Vec3 velocity, double interval) {
    if (!eligible(portal, center, velocity)) return velocity;
    Vec3 sourceVelocity = velocity.scale(1 / VELOCITY_UNIT);
    Vec3 delta = portal.center().subtract(center).scale(1 / SOURCE_UNIT);
    if (sourceVelocity.y < 0) {
      double speedGain = 1 - sourceVelocity.horizontalDistanceSqr() / 16384;
      double heightGain = Math.clamp((-delta.y - 128) / 128, 0, 1);
      double gain = speedGain * heightGain;
      if (gain == 0) return velocity;
      return new Vec3(
          (sourceVelocity.x + (delta.x - sourceVelocity.x) * gain) * VELOCITY_UNIT,
          velocity.y,
          (sourceVelocity.z + (delta.z - sourceVelocity.z) * gain) * VELOCITY_UNIT);
    }
    Vec3 normal = portal.normal();
    Vec3 tangent = delta.subtract(normal.scale(delta.dot(normal)));
    Vec3 planarVelocity = sourceVelocity.subtract(normal.scale(sourceVelocity.dot(normal)));
    double away = tangent.dot(planarVelocity);
    if (away >= 0) return velocity;
    double scale = Math.min(-away / tangent.length(), 2 * interval);
    return velocity.add(tangent.scale(scale * VELOCITY_UNIT));
  }

  private static Vec3 horizontal(Vec3 vector) {
    double length = Math.sqrt(vector.x * vector.x + vector.z * vector.z) + 1.1920928955078125e-7;
    return new Vec3(vector.x / length, 0, vector.z / length);
  }
}
