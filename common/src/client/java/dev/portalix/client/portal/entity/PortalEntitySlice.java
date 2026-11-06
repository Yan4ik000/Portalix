package dev.portalix.client.portal.entity;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalRoutes;
import dev.portalix.portal.geometry.PortalAperture;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public record PortalEntitySlice(
    List<PortalLink> frame,
    PortalEndpoint portal,
    AABB bounds,
    boolean replica,
    Vec3 anchor,
    int light,
    PortalEntityShadows shadow,
    boolean oval) {
  public PortalEntitySlice(
      List<PortalLink> frame,
      PortalEndpoint portal,
      AABB bounds,
      boolean replica,
      Vec3 anchor,
      int light) {
    this(
        frame,
        portal,
        bounds,
        replica,
        anchor,
        light,
        null,
        portal != null && portal.normal().y > .999);
  }

  public PortalEntitySlice withLight(int value) {
    return new PortalEntitySlice(frame, portal, bounds, replica, anchor, value, shadow, oval);
  }

  public PortalEntitySlice withShadow(PortalEntityShadows value) {
    return new PortalEntitySlice(frame, portal, bounds, replica, anchor, light, value, oval);
  }

  public Vec3 position(Vec3 position) {
    for (var link : frame) position = link.transform().point(position);
    return position;
  }

  public Quaternionf rotation() {
    var rotation = new Quaternionf();
    for (var link : frame) rotation.premul(link.transform().rotation());
    return rotation;
  }

  public AABB bodyBounds(AABB body) {
    for (var link : frame) body = link.transform().bounds(body);
    return body.move(anchor.subtract(body.getCenter()));
  }

  public boolean visibleTo(
      boolean firstPerson,
      List<PortalLink> rootFrame,
      List<PortalLink> cameraFrame,
      List<PortalLink> view,
      Vec3 camera) {
    if (!firstPerson) return true;
    if (frame.equals(cameraFrame)) return false;
    if (view.isEmpty()) return portal != null && PortalAperture.signedDistance(portal, camera) > 0;
    return frame.equals(rootFrame) || view.getLast().exit().endpoint().equals(portal);
  }

  public static List<PortalEntitySlice> split(
      List<PortalLink> history, PortalLink crossing, AABB bounds) {
    return split(history, crossing, bounds, bounds.getCenter());
  }

  public static List<PortalEntitySlice> split(
      List<PortalLink> history, PortalLink crossing, AABB bounds, Vec3 anchor) {
    var frame = new ArrayList<PortalLink>();
    for (var link : history) {
      PortalRoutes.append(frame, link);
      bounds = link.transform().bounds(bounds);
      anchor = link.transform().point(anchor);
    }
    boolean oval =
        crossing != null
            && (crossing.entrance().endpoint().normal().y > .999
                || crossing.exit().endpoint().normal().y > .999);
    var source =
        new PortalEntitySlice(
            List.copyOf(frame),
            crossing == null ? null : crossing.entrance().endpoint(),
            bounds,
            false,
            anchor,
            -1,
            null,
            oval);
    if (crossing == null) return List.of(source);
    PortalRoutes.append(frame, crossing);
    return List.of(
        source,
        new PortalEntitySlice(
            List.copyOf(frame),
            crossing.exit().endpoint(),
            crossing.transform().bounds(bounds),
            true,
            crossing.transform().point(anchor),
            -1,
            null,
            oval));
  }

}
