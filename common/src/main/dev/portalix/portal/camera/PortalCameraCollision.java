package dev.portalix.portal.camera;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalRoutes;
import dev.portalix.portal.PortalViewLimits;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.geometry.PortalEllipse;
import dev.portalix.portal.traversal.PortalCollisionWorld;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class PortalCameraCollision {
  public static final double RADIUS = .1;
  static final double CLEARANCE = 1e-4;

  private PortalCameraCollision() {}

  public static Result solve(
      Vec3 origin,
      Vec3 direction,
      double requested,
      List<PortalLink> links,
      Query query) {
    return solve(origin, direction, requested, links, query, List.of());
  }

  static Result solve(Vec3 origin, Vec3 direction, double requested, List<PortalLink> links,
      Query query, List<PortalLink> initialFrame) {
    return solve(origin, direction, requested, links, query, initialFrame, link -> true);
  }

  static Result solve(Vec3 origin, Vec3 direction, double requested, List<PortalLink> links,
      Query query, List<PortalLink> initialFrame, Predicate<PortalLink> ready) {
    var path = new ArrayList<PortalLink>();
    List<PortalLink> frame = initialFrame;
    var zeroProgress = new HashSet<PortalLink>();
    Vec3 position = origin;
    double directionLength = direction.length();
    if (directionLength == 0 || requested <= 0) return new Result(origin, List.of(), 0, null);
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
      if (crossing != null && !ready.test(crossing)) {
        var portal = crossing.entrance().endpoint();
        double contact = (PortalAperture.signedDistance(portal, position) - projectedRadius(portal.normal()))
            / -movement.dot(portal.normal());
        double solid = query.scene(body, segment, null).sweepFraction(body, segment) * limit;
        double travel = Math.max(0, remaining * Math.min(contact, solid) - CLEARANCE);
        boolean blocked = PortalViewLimits.canCross(frame, crossing)
            && openingFraction(crossing, position, segment) >= 1
            && query.scene(body, segment, crossing).sweepFraction(body, segment) >= 1;
        return new Result(position.add(heading.scale(travel)), List.copyOf(path), distance + travel,
            blocked ? crossing : null);
      }
      PortalLink opening = crossing;
      if (opening == null) {
        double nearest = Double.POSITIVE_INFINITY;
        for (var link : links) {
          if (!ready.test(link)) continue;
          var portal = link.entrance().endpoint();
          double depth = PortalAperture.signedDistance(portal, position);
          if (Math.abs(depth) < nearest && touchesOpening(portal, position, bounds, depth)) {
            nearest = Math.abs(depth);
            opening = link;
          }
        }
      }
      double fraction = query.scene(body, segment, opening).sweepFraction(body, segment);
      if (opening != null)
        fraction = Math.min(fraction, openingFraction(opening, position, segment));
      double length = segment.length();
      if (fraction < 1 || crossing != null && !PortalViewLimits.canCross(frame, crossing)) {
        double travel = Math.max(0, length * fraction - CLEARANCE);
        return new Result(position.add(heading.scale(travel)), List.copyOf(path), distance + travel, null);
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
      if (frame == initialFrame) frame = new ArrayList<>(initialFrame);
      PortalRoutes.append(frame, crossing);
      path.add(crossing);
    }
    return new Result(position, List.copyOf(path), distance, null);
  }

  public static boolean touchesOpening(PortalEndpoint portal, Vec3 origin, AABB sweptVolume) {
    return touchesOpening(portal, origin, sweptVolume, PortalAperture.signedDistance(portal, origin));
  }

  private static boolean touchesOpening(
      PortalEndpoint portal, Vec3 origin, AABB sweptVolume, double depth) {
    return PortalAperture.touches(portal, sweptVolume)
        || depth <= 0 && depth >= -PortalAperture.CAVITY_DEPTH
            && PortalAperture.containsPassage(portal, origin);
  }

  private static double openingFraction(PortalLink link, Vec3 position, Vec3 movement) {
    // A floor exit has a narrower opening than the wall entrance feeding it.
    var portal = link.entrance().endpoint();
    double depth = PortalAperture.signedDistance(portal, position);
    double travel = movement.dot(portal.normal());
    var slab = interval(depth, travel, projectedRadius(portal.normal()));
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
        var range = interval(relative.dot(axis), movement.dot(axis), half - projectedRadius(axis));
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

  static double projectedRadius(Vec3 axis) {
    return RADIUS * (Math.abs(axis.x) + Math.abs(axis.y) + Math.abs(axis.z));
  }

  @FunctionalInterface
  public interface Query {
    PortalCollisionWorld scene(AABB body, Vec3 movement, PortalLink opening);
  }

  public record Result(Vec3 position, List<PortalLink> links, double distance, @Nullable PortalLink blocked) {}
}
