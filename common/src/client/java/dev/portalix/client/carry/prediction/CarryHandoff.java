package dev.portalix.client.carry.prediction;

import net.minecraft.world.phys.Vec3;

record CarryHandoff(Vec3 position, Vec3 velocity, double start) {
  public static final double DURATION = 3;

  public Vec3 offset(double time) {
    double t = Math.clamp((time - start) / DURATION, 0, 1);
    return position
        .scale(1 - t * t * (3 - 2 * t))
        .add(velocity.scale(DURATION * t * (1 - t) * (1 - t)));
  }

  public Vec3 movement(double time) {
    double t = Math.clamp((time - start) / DURATION, 0, 1);
    return position.scale(6 * t * (t - 1) / DURATION).add(velocity.scale(1 - 4 * t + 3 * t * t));
  }

  public boolean complete(double time) {
    return time >= start + DURATION;
  }
}
