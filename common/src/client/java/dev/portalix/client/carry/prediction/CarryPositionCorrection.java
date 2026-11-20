package dev.portalix.client.carry.prediction;

import dev.portalix.portal.geometry.PortalTransform;
import net.minecraft.world.phys.Vec3;

record CarryPositionCorrection(Vec3 positionOffset, Vec3 velocityOffset, double start, double duration) {
  static final double DEFAULT_DURATION = 3;

  CarryPositionCorrection(Vec3 positionOffset, Vec3 velocityOffset, double start) {
    this(positionOffset, velocityOffset, start, DEFAULT_DURATION);
  }

  CarryPositionCorrection transform(PortalTransform transform) {
    return new CarryPositionCorrection(
        transform.vector(positionOffset), transform.vector(velocityOffset), start, duration);
  }

  Vec3 offset(double time) {
    double t = Math.clamp((time - start) / duration, 0, 1);
    return positionOffset
        .scale(1 - t * t * (3 - 2 * t))
        .add(velocityOffset.scale(duration * t * (1 - t) * (1 - t)));
  }

  Vec3 velocity(double time) {
    double t = Math.clamp((time - start) / duration, 0, 1);
    return positionOffset.scale(6 * t * (t - 1) / duration)
        .add(velocityOffset.scale(1 - 4 * t + 3 * t * t));
  }

  double boundsPadding() {
    return positionOffset.length() + velocityOffset.length() * duration;
  }

  boolean complete(double time) {
    return time >= start + duration;
  }
}
