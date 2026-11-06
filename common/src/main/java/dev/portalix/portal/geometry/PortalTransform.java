package dev.portalix.portal.geometry;

import dev.portalix.api.PortalEndpoint;
import java.util.Objects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

public record PortalTransform(PortalEndpoint entrance, PortalEndpoint exit) {
  public PortalTransform {
    Objects.requireNonNull(entrance);
    Objects.requireNonNull(exit);
  }

  public Vec3 point(Vec3 point) {
    return point(entrance, exit, entrance.right(), exit.right(), point);
  }

  public Vec3 inversePoint(Vec3 point) {
    return point(exit, entrance, exit.right(), entrance.right(), point);
  }

  private static Vec3 point(
      PortalEndpoint entrance,
      PortalEndpoint exit,
      Vec3 entranceRight,
      Vec3 exitRight,
      Vec3 point) {
    double x = point.x - entrance.center().x;
    double y = point.y - entrance.center().y;
    double z = point.z - entrance.center().z;
    Vec3 entranceUp = entrance.up(), entranceNormal = entrance.normal();
    double right = -(x * entranceRight.x + y * entranceRight.y + z * entranceRight.z);
    double up = x * entranceUp.x + y * entranceUp.y + z * entranceUp.z;
    double normal = -(x * entranceNormal.x + y * entranceNormal.y + z * entranceNormal.z);
    Vec3 exitUp = exit.up(), exitNormal = exit.normal();
    return new Vec3(
        exit.center().x + (exitRight.x * right + exitUp.x * up + exitNormal.x * normal),
        exit.center().y + (exitRight.y * right + exitUp.y * up + exitNormal.y * normal),
        exit.center().z + (exitRight.z * right + exitUp.z * up + exitNormal.z * normal));
  }

  public Vec3 vector(Vec3 vector) {
    double right = -vector.dot(entrance.right());
    double up = vector.dot(entrance.up());
    double normal = -vector.dot(entrance.normal());
    Vec3 exitRight = exit.right(), exitUp = exit.up(), exitNormal = exit.normal();
    return new Vec3(
        exitRight.x * right + exitUp.x * up + exitNormal.x * normal,
        exitRight.y * right + exitUp.y * up + exitNormal.y * normal,
        exitRight.z * right + exitUp.z * up + exitNormal.z * normal);
  }

  public PortalTransform inverse() {
    return new PortalTransform(exit, entrance);
  }

  public Quaternionf rotation() {
    Vec3 x = vector(new Vec3(1, 0, 0));
    Vec3 y = vector(new Vec3(0, 1, 0));
    Vec3 z = vector(new Vec3(0, 0, 1));
    return new Quaternionf()
        .setFromNormalized(
            new Matrix3f(
                (float) x.x,
                (float) x.y,
                (float) x.z,
                (float) y.x,
                (float) y.y,
                (float) y.z,
                (float) z.x,
                (float) z.y,
                (float) z.z));
  }

  public AABB bounds(AABB box) {
    Vec3 center = point(box.getCenter());
    var inverse = inverse();
    double x = PortalAperture.supportRadius(box, inverse.vector(new Vec3(1, 0, 0)));
    double y = PortalAperture.supportRadius(box, inverse.vector(new Vec3(0, 1, 0)));
    double z = PortalAperture.supportRadius(box, inverse.vector(new Vec3(0, 0, 1)));
    return new AABB(
        center.x - x, center.y - y, center.z - z, center.x + x, center.y + y, center.z + z);
  }
}
