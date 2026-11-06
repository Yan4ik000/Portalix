package dev.portalix.client.portal.entity;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.geometry.PortalAperture;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;

public record PortalEntityClipMask(
    PortalEndpoint portal, Vec3 anchor, boolean replica, boolean oval, boolean shadow) {
  public PortalEntityClipMask(PortalEndpoint portal, Vec3 anchor, boolean replica) {
    this(portal, anchor, replica, portal.normal().y > .999, false);
  }

  public float bias() {
    return shadow ? (float) PortalAperture.PLANE_EPSILON : 0;
  }

  public Vector4f[] aperture() {
    if (!oval) return edges();
    var relative = anchor.subtract(portal.center());
    double x = relative.dot(portal.right()), y = relative.dot(portal.up());
    double distance = Math.abs(relative.dot(portal.normal()));
    double direction = replica ? 1 : -1;
    double width = portal.width() / 2, height = portal.height() / 2;
    var horizontal =
        new Vector4f((float) (distance / width), 0, (float) (direction * x / width), 0);
    var vertical =
        new Vector4f(0, (float) (distance / height), (float) (direction * y / height), 0);
    var depth = new Vector4f(0, 0, (float) direction, (float) distance);
    float scale = Math.max(Math.max(horizontal.length(), vertical.length()), depth.length());
    return new Vector4f[] {
      horizontal.div(scale), vertical.div(scale), depth.div(scale), new Vector4f(0, 0, 0, 1)
    };
  }

  public Vector4f[] edges() {
    var relative = anchor.subtract(portal.center());
    double x = relative.dot(portal.right());
    double y = relative.dot(portal.up());
    double distance = Math.abs(relative.dot(portal.normal()));
    double direction = replica ? 1 : -1;
    double width = portal.width() / 2, height = portal.height() / 2;
    return new Vector4f[] {
      edge(-distance, 0, direction * (width - x), width * distance),
      edge(distance, 0, direction * (width + x), width * distance),
      edge(0, -distance, direction * (height - y), height * distance),
      edge(0, distance, direction * (height + y), height * distance)
    };
  }

  private static Vector4f edge(double x, double y, double z, double w) {
    double scale = Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.max(Math.abs(z), Math.abs(w)));
    if (scale == 0) return new Vector4f(0, 0, 0, 1);
    return new Vector4f(
        (float) (x / scale), (float) (y / scale), (float) (z / scale), (float) (w / scale));
  }
}
