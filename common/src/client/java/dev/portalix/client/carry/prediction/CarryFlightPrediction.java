package dev.portalix.client.carry.prediction;

import dev.portalix.portal.geometry.PortalTransform;
import net.minecraft.world.phys.Vec3;

final class CarryFlightPrediction {
  private long tick;
  private double time;
  private Vec3 position;
  private Vec3 velocity;
  private CarryHandoff correction;

  CarryFlightPrediction(
      long tick,
      double time,
      Vec3 position,
      Vec3 velocity,
      double now,
      Vec3 displayed,
      Vec3 movement) {
    this.tick = tick;
    this.time = time;
    this.position = position;
    this.velocity = velocity;
    correction = new CarryHandoff(displayed.subtract(base(now)), movement.subtract(velocity), now);
  }

  private Vec3 base(double now) {
    return position.add(velocity.scale(now - time));
  }

  void transform(PortalTransform transform) {
    position = transform.point(position);
    velocity = transform.vector(velocity);
    correction =
        new CarryHandoff(
            transform.vector(correction.position()),
            transform.vector(correction.velocity()),
            correction.start());
  }

  Vec3 position(double now) {
    return base(now).add(correction.offset(now));
  }

  Vec3 velocity(double now) {
    return velocity.add(correction.movement(now));
  }

  void update(
      long tick, Vec3 position, Vec3 velocity, double now, Vec3 displayed, Vec3 movement) {
    if (tick <= this.tick) return;
    time += tick - this.tick;
    this.tick = tick;
    this.position = position;
    this.velocity = velocity;
    correction = new CarryHandoff(displayed.subtract(base(now)), movement.subtract(velocity), now);
  }
}
