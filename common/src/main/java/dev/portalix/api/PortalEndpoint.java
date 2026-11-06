package dev.portalix.api;

import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record PortalEndpoint(
    ResourceKey<Level> dimension, Vec3 center, Vec3 normal, Vec3 up, double width, double height) {
  public PortalEndpoint {
    Objects.requireNonNull(dimension);
    Objects.requireNonNull(center);
    Objects.requireNonNull(normal);
    Objects.requireNonNull(up);
    if (width != 1.0 || height != 2.0) {
      throw new IllegalArgumentException("Only 1x2 portals are supported");
    }
    if (!finite(center) || !finite(normal) || !finite(up)) {
      throw new IllegalArgumentException("Portal geometry must be finite and non-degenerate");
    }
    normal = unit(normal);
    up = unit(up);
    up = up.subtract(normal.scale(up.dot(normal)));
    if (up.lengthSqr() < 1.0E-12) {
      throw new IllegalArgumentException("Portal up must not be parallel to its normal");
    }
    up = unit(up);
  }

  private static boolean finite(Vec3 v) {
    return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
  }

  private static Vec3 unit(Vec3 vector) {
    double scale = Math.max(Math.abs(vector.x), Math.max(Math.abs(vector.y), Math.abs(vector.z)));
    if (scale == 0) throw new IllegalArgumentException("Portal direction must be nonzero");
    Vec3 scaled = new Vec3(vector.x / scale, vector.y / scale, vector.z / scale);
    return scaled.scale(1.0 / Math.sqrt(scaled.lengthSqr()));
  }

  public Vec3 right() {
    return this.up.cross(this.normal).normalize();
  }
}
