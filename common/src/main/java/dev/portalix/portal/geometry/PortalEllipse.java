package dev.portalix.portal.geometry;

import dev.portalix.api.PortalEndpoint;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;

public record PortalEllipse(PortalEndpoint portal, double margin) {
  private Vec3 coordinates(Vec3 point) {
    Vec3 relative = point.subtract(portal.center());
    return new Vec3(
        relative.dot(portal.right()) / (PortalAperture.passageHalfWidth(portal) + margin),
        0,
        relative.dot(portal.up()) / (PortalAperture.passageHalfHeight(portal) + margin));
  }

  public double metric(Vec3 point) {
    return coordinates(point).lengthSqr();
  }

  public Vec3 normal(Vec3 point) {
    Vec3 local = coordinates(point);
    return portal
        .right()
        .scale(local.x / (PortalAperture.passageHalfWidth(portal) + margin))
        .add(portal.up().scale(local.z / (PortalAperture.passageHalfHeight(portal) + margin)))
        .normalize();
  }

  public Vec3 project(Vec3 point) {
    double metric = metric(point);
    if (metric < 1 - 1e-9) return point;
    Vec3 relative = point.subtract(portal.center());
    double correction = (1 - 1e-9) / Math.sqrt(metric) - 1;
    return point
        .add(portal.right().scale(relative.dot(portal.right()) * correction))
        .add(portal.up().scale(relative.dot(portal.up()) * correction));
  }

  public Optional<Interval> interval(Vec3 start, Vec3 movement) {
    Vec3 local = coordinates(start);
    Vec3 delta =
        new Vec3(
            movement.dot(portal.right()) / (PortalAperture.passageHalfWidth(portal) + margin),
            0,
            movement.dot(portal.up()) / (PortalAperture.passageHalfHeight(portal) + margin));
    double a = delta.lengthSqr(), b = 2 * local.dot(delta), c = local.lengthSqr() - 1;
    if (Math.abs(c) <= 1e-9) c = 0;
    if (a == 0)
      return c < 0
          ? Optional.of(new Interval(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY))
          : Optional.empty();
    double discriminant = b * b - 4 * a * c;
    if (discriminant <= 0) return Optional.empty();
    double q = -.5 * (b + Math.copySign(Math.sqrt(discriminant), b));
    double first = q / a, second = c / q;
    return Optional.of(new Interval(Math.min(first, second), Math.max(first, second)));
  }

  public record Interval(double enter, double leave) {}
}
