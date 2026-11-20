package dev.portalix.portal.camera;

import dev.portalix.portal.geometry.PortalTransform;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3d;

public record PortalLookAngles(float yaw, float pitch) {
  public static PortalLookAngles from(Quaternionfc orientation) {
    return from(new Quaterniond(orientation));
  }

  public static PortalLookAngles from(Quaterniondc orientation) {
    var left = new Quaterniond(orientation).normalize().transform(new Vector3d(-1, 0, 0));
    return from(orientation, (float) Math.toDegrees(Math.atan2(left.z, left.x)));
  }

  public static PortalLookAngles from(Quaterniondc orientation, float poleYaw) {
    var rotation = new Quaterniond(orientation).normalize();
    var forward = rotation.transform(new Vector3d(0, 0, -1));
    double horizontal = Math.hypot(forward.x, forward.z);
    if (horizontal <= 1e-6) {
      return new PortalLookAngles(poleYaw, Math.copySign(90f, (float) -forward.y));
    }
    return new PortalLookAngles(
        (float) Math.toDegrees(Math.atan2(-forward.x, forward.z)),
        (float) Math.toDegrees(Math.atan2(-forward.y, horizontal)));
  }

  public static PortalLookAngles transform(PortalTransform transform, float yaw, float pitch) {
    var rotation =
        new Quaterniond()
            .rotationYXZ(Math.PI - Math.toRadians(yaw), -Math.toRadians(pitch), 0)
            .premul(new Quaterniond(transform.rotation()));
    var heading =
        transform.vector(
            new Vec3(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw))));
    float poleYaw =
        Math.hypot(heading.x, heading.z) > 1e-6
            ? (float) Math.toDegrees(Math.atan2(-heading.x, heading.z))
            : from(rotation).yaw();
    return from(rotation, poleYaw);
  }

  public static Quaternionf rotation(double yaw, double pitch) {
    return new Quaterniond()
        .rotationYXZ(Math.PI - Math.toRadians(yaw), -Math.toRadians(pitch), 0)
        .get(new Quaternionf());
  }
}
