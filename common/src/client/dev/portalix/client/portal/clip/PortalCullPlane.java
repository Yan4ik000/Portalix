package dev.portalix.client.portal.clip;

import dev.portalix.api.PortalEndpoint;
import net.minecraft.world.phys.Vec3;

public record PortalCullPlane(double x, double y, double z, double offset) {
  public static PortalCullPlane of(PortalEndpoint exit) {
    var normal = exit.normal();
    return new PortalCullPlane(normal.x, normal.y, normal.z, -normal.dot(exit.center()) + .001);
  }

  public PortalCullPlane relativeTo(Vec3 camera) {
    return new PortalCullPlane(x, y, z, offset + x * camera.x + y * camera.y + z * camera.z);
  }

  public double distance(double px, double py, double pz) {
    return x * px + y * py + z * pz + offset;
  }

  public boolean intersects(
      double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    return distance(x < 0 ? minX : maxX, y < 0 ? minY : maxY, z < 0 ? minZ : maxZ) >= 0;
  }

  public boolean contains(
      double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    return distance(x < 0 ? maxX : minX, y < 0 ? maxY : minY, z < 0 ? maxZ : minZ) >= 0;
  }
}
