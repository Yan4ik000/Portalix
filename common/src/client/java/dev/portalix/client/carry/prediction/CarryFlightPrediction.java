package dev.portalix.client.carry.prediction;

import dev.portalix.portal.geometry.PortalTransform;
import net.minecraft.world.phys.Vec3;

final class CarryFlightPrediction {
  private long tick;
  private double tickDuration = 1;
  private Vec3 position;
  private Vec3 velocity;
  private CarryPositionCorrection correction;

  CarryFlightPrediction(
      long tick,
      Vec3 position,
      Vec3 velocity,
      double tickDuration,
      double now,
      Vec3 displayed,
      Vec3 movement) {
    setSnapshot(tick, position, velocity, tickDuration, now, displayed, movement);
  }

  void transform(PortalTransform transform) {
    position = transform.point(position);
    velocity = transform.vector(velocity);
    correction = correction.transform(transform);
  }

  Vec3 position(double now) {
    double elapsed = Math.max(0, (now - correction.start()) / tickDuration);
    // A snapshot predicts the next physics step. Bring extrapolation to rest over one
    // further step if the next snapshot is late, preserving velocity at the boundary.
    double braking = Math.clamp(elapsed - 1, 0, 1);
    double travel = Math.min(elapsed, 1) + braking * (1 - .5 * braking);
    return position.add(velocity.scale(travel)).add(correction.offset(now));
  }

  Vec3 velocity(double now) {
    double elapsed = (now - correction.start()) / tickDuration;
    return velocity.scale(Math.clamp(2 - elapsed, 0, 1) / tickDuration)
        .add(correction.velocity(now));
  }

  void update(
      long tick, Vec3 position, Vec3 velocity, double tickDuration, double now, Vec3 displayed, Vec3 movement) {
    if (tick <= this.tick) return;
    setSnapshot(tick, position, velocity, tickDuration, now, displayed, movement);
  }

  private void setSnapshot(long tick, Vec3 position, Vec3 velocity, double tickDuration,
      double now, Vec3 displayed, Vec3 movement) {
    this.tick = tick;
    double blending = CarryPositionCorrection.DEFAULT_DURATION * Math.min(1, Math.min(this.tickDuration, tickDuration));
    this.tickDuration = tickDuration;
    this.position = position;
    this.velocity = velocity;
    correction = new CarryPositionCorrection(displayed.subtract(position),
        movement.subtract(velocity.scale(1 / tickDuration)), now, blending);
  }
}
