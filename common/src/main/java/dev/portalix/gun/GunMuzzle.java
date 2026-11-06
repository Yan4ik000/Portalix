package dev.portalix.gun;

import net.minecraft.world.phys.Vec3;

public final class GunMuzzle {
  private GunMuzzle() {}

  public static Vec3 position(Vec3 eye, Vec3 look, float yawDegrees, boolean left) {
    Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
    if (right.lengthSqr() < 1.0E-8) {
      double yaw = Math.toRadians(yawDegrees);
      right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
    }

    right = right.normalize();
    Vec3 up = right.cross(look).normalize();
    return eye.add(look.scale(0.8)).add(right.scale(left ? -0.35 : 0.35)).add(up.scale(-0.36));
  }
}
