package dev.portalix.portal.camera;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.geometry.PortalEllipse;
import dev.portalix.portal.traversal.PortalCollisionWorld;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PortalCameraCollision {
  public static final double RADIUS = .1;
  private static final double CLEARANCE = 1e-4;

  private PortalCameraCollision() {}

  public static Result solve(
      Vec3 origin,
      Vec3 direction,
      double requested,
      List<PortalLink> links,
      Query query) {
    var path = new ArrayList<PortalLink>();
    var zeroProgress = new HashSet<PortalLink>();
    Vec3 position = origin;
    double directionLength = direction.length();
    if (directionLength == 0 || requested <= 0) return new Result(origin, List.of(), 0);
    Vec3 heading = direction.scale(1 / directionLength);
    double remaining = requested, distance = 0;
    while (remaining > 0) {
      Vec3 movement = heading.scale(remaining);
      PortalLink crossing = null;
      double limit = 1;
      for (var link : links) {
        var portal = link.entrance().endpoint();
        if (PortalAperture.signedDistance(portal, position) < -PortalAperture.PLANE_EPSILON) continue;
        var candidate = PortalAperture.crossing(portal, position, movement);
        if (candidate.isPresent() && candidate.orElseThrow().fraction() < limit) {
          limit = candidate.orElseThrow().fraction();
          crossing = link;
        }
      }
      var body = new AABB(position, position).inflate(RADIUS);
      Vec3 segment = movement.scale(limit);
      var bounds = body.expandTowards(segment);
      PortalLink opening = crossing;
      if (opening == null) {
        double nearest = Double.POSITIVE_INFINITY;
        for (var link : links) {
          var portal = link.entrance().endpoint();
          double depth = PortalAperture.signedDistance(portal, position);
          if ((PortalAperture.touches(portal, bounds)
                  || depth <= 0 && depth >= -PortalAperture.CAVITY_DEPTH
                      && PortalAperture.containsPassage(portal, position))
              && Math.abs(depth) < nearest) {
            nearest = Math.abs(depth);
            opening = link;
          }
        }
      }
      double fraction = query.scene(body, segment, opening).sweepFraction(body, segment);
      if (opening != null)
        fraction = Math.min(fraction, openingFraction(opening, position, segment));
      double length = segment.length();
      if (fraction < 1) {
        double travel = Math.max(0, length * fraction - CLEARANCE);
        return new Result(position.add(heading.scale(travel)), List.copyOf(path), distance + travel);
      }
      position = position.add(segment);
      distance += length;
      remaining -= length;
      if (crossing == null) break;
      if (length > 1e-9) zeroProgress.clear();
      else if (!zeroProgress.add(crossing)) break;
      position = crossing.transform().point(position);
      var exit = crossing.exit().endpoint();
      position = position.subtract(exit.normal().scale(PortalAperture.signedDistance(exit, position)));
      heading = crossing.transform().vector(heading);
      path.add(crossing);
    }
    return new Result(position, List.copyOf(path), distance);
  }

  private static double openingFraction(PortalLink link, Vec3 position, Vec3 movement) {
    // A floor exit has a narrower opening than the wall entrance feeding it.
    var portal = link.entrance().endpoint();
    double depth = PortalAperture.signedDistance(portal, position);
    double travel = movement.dot(portal.normal());
    double radius =
        RADIUS
            * (Math.abs(portal.normal().x)
                + Math.abs(portal.normal().y)
                + Math.abs(portal.normal().z));
    var slab = interval(depth, travel, radius);
    if (slab == null || slab.leave() <= 0 || slab.enter() >= 1) return 1;
    double enter = Math.max(0, slab.enter()), leave = Math.min(1, slab.leave());
    var entrance = apertureInterval(portal, position, movement);
    var exit =
        apertureInterval(
            link.exit().endpoint(),
            link.transform().point(position),
            link.transform().vector(movement));
    if (entrance == null || exit == null) return travel > 0 && enter == 0 ? 1 : enter;
    double clearEnter = Math.max(entrance.enter(), exit.enter());
    double clearLeave = Math.min(entrance.leave(), exit.leave());
    if (clearEnter > enter || clearLeave < enter)
      return travel > 0 && enter == 0 ? 1 : enter;
    return clearLeave < leave ? Math.max(0, clearLeave) : 1;
  }

  private static PortalEllipse.Interval apertureInterval(
      PortalEndpoint portal, Vec3 position, Vec3 movement) {
    double enter = Double.NEGATIVE_INFINITY, leave = Double.POSITIVE_INFINITY;
    if (portal.normal().y == 1) {
      var ellipse = new PortalEllipse(portal, 0);
      for (double x : new double[] {-RADIUS, RADIUS}) {
        for (double z : new double[] {-RADIUS, RADIUS}) {
          var range = ellipse.interval(position.add(x, 0, z), movement);
          if (range.isEmpty()) return null;
          enter = Math.max(enter, range.orElseThrow().enter());
          leave = Math.min(leave, range.orElseThrow().leave());
        }
      }
    } else {
      Vec3 relative = position.subtract(portal.center());
      var axes = List.of(portal.right(), portal.up());
      for (int index = 0; index < axes.size(); index++) {
        var axis = axes.get(index);
        double half =
            index == 0
                ? PortalAperture.passageHalfWidth(portal)
                : PortalAperture.passageHalfHeight(portal);
        double radius = RADIUS * (Math.abs(axis.x) + Math.abs(axis.y) + Math.abs(axis.z));
        var range = interval(relative.dot(axis), movement.dot(axis), half - radius);
        if (range == null) return null;
        enter = Math.max(enter, range.enter());
        leave = Math.min(leave, range.leave());
      }
    }
    return enter < leave ? new PortalEllipse.Interval(enter, leave) : null;
  }

  private static PortalEllipse.Interval interval(double position, double movement, double radius) {
    if (radius < 0) return null;
    if (movement == 0)
      return Math.abs(position) <= radius
          ? new PortalEllipse.Interval(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)
          : null;
    double first = (-radius - position) / movement, last = (radius - position) / movement;
    return new PortalEllipse.Interval(Math.min(first, last), Math.max(first, last));
  }

  @FunctionalInterface
  public interface Query {
    PortalCollisionWorld scene(AABB body, Vec3 movement, PortalLink opening);
  }

  public record Result(Vec3 position, List<PortalLink> links, double distance) {}
}
