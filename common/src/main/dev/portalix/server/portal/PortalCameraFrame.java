package dev.portalix.server.portal;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalRoutes;
import dev.portalix.portal.PortalViewLimits;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.geometry.PortalTransform;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class PortalCameraFrame {
  private static final double WITNESS_EPSILON = 1e-7;
  // At the orbit recovery rate (12/s), six ticks cover over 95% of the return movement.
  static final int RECHECK_TICKS = 6;
  private long nextValidation;
  private boolean returning;
  private List<PortalLink> frame = List.of();

  public List<PortalLink> frame() {
    return frame;
  }

  public void clear() {
    frame = List.of();
    nextValidation = 0;
    returning = false;
  }

  boolean stillReachable(long tick, BooleanSupplier reachable) {
    if (frame.isEmpty() || tick < nextValidation) return true;
    nextValidation = tick + RECHECK_TICKS;
    if (reachable.getAsBoolean()) returning = false;
    else if (returning) return false;
    else returning = true;
    return true;
  }

  public void crossed(PortalLink link) {
    nextValidation = 0;
    returning = false;
    var rebased = new ArrayList<PortalLink>(frame.size() + 1);
    rebased.add(link.inverse());
    rebased.addAll(frame);
    frame = PortalRoutes.simplify(rebased);
    if (frame.size() > PortalViewLimits.CAMERA_DEPTH) clear();
  }

  public boolean request(List<PortalLink> candidate, List<PortalLink> eyePath, Vec3 previousEye,
      Vec3 bodyEye, double boomReach, double eyeAllowance, Predicate<PortalLink> eligible) {
    for (int index = 1; index < candidate.size(); index++)
      if (PortalRoutes.sameIdentity(candidate.get(index - 1).inverse(), candidate.get(index)))
        return false;
    if (!prefix(candidate, frame)) {
      if (!reachable(candidate, eyePath, previousEye, bodyEye, boomReach, eyeAllowance, eligible)) return false;
      returning = false;
      nextValidation = 0;
    }
    frame = List.copyOf(candidate);
    return true;
  }

  static boolean reachable(List<PortalLink> candidate, List<PortalLink> eyePath, Vec3 previousEye,
      Vec3 bodyEye, double boomReach, double eyeAllowance, Predicate<PortalLink> eligible) {
    if (PortalRoutes.matches(candidate, eyePath)) return true;
    List<PortalLink> remaining = candidate;
    double reach = boomReach + eyeAllowance;
    if (!eyePath.isEmpty() && prefix(eyePath, candidate)) {
      // The body-to-eye segment has already crossed these apertures. Only the boom remains.
      for (var link : eyePath) bodyEye = link.transform().point(bodyEye);
      previousEye = bodyEye;
      remaining = candidate.subList(eyePath.size(), candidate.size());
      reach = boomReach;
    }
    if (remaining.stream().anyMatch(eligible.negate())) return false;
    return hasWitness(remaining, previousEye, bodyEye, reach);
  }

  private static boolean prefix(List<PortalLink> prefix, List<PortalLink> path) {
    return prefix.size() <= path.size() && PortalRoutes.matches(prefix, path.subList(0, prefix.size()));
  }

  private static boolean traversable(List<PortalLink> path, Vec3 position, Vec3[] points) {
    var zeroProgress = new HashSet<PortalLink>();
    for (int index = 0; index < path.size(); index++) {
      var link = path.get(index);
      var entrance = link.entrance().endpoint();
      Vec3 point = points[index];
      Vec3 exitPoint = link.transform().point(point);
      Vec3 movement = point.subtract(position);
      if (PortalAperture.signedDistance(entrance, position) < -PortalAperture.PLANE_EPSILON
          || Math.abs(PortalAperture.signedDistance(entrance, point)) > PortalAperture.PLANE_EPSILON
          || !containsWitness(entrance, point)
          || !containsWitness(link.exit().endpoint(), exitPoint))
        return false;
      if (movement.lengthSqr() > 1e-18) {
        if (movement.dot(entrance.normal()) >= 0) return false;
        zeroProgress.clear();
      } else if (!zeroProgress.add(link)) return false;
      position = exitPoint;
    }
    return true;
  }

  private static boolean hasWitness(List<PortalLink> path, Vec3 from, Vec3 to, double reach) {
    int count = path.size();
    if (count == 0) return true;
    var entrance = path.getFirst().entrance().endpoint();
    double startDepth = PortalAperture.signedDistance(entrance, from);
    double endDepth = PortalAperture.signedDistance(entrance, to);
    Vec3 front = startDepth >= endDepth ? from : to;
    Vec3 back = startDepth >= endDepth ? to : from;
    double frontDepth = Math.max(startDepth, endDepth);
    double backDepth = Math.min(startDepth, endDepth);
    if (frontDepth < -PortalAperture.PLANE_EPSILON) return false;
    if (frontDepth >= 0 && backDepth < 0)
      back = front.lerp(back, frontDepth / (frontDepth - backDepth));
    var points = new Vec3[count];
    var gates = new Gate[count];
    for (int index = 0; index < count; index++) {
      var next = index + 1 == count ? null : path.get(index + 1).entrance().endpoint();
      var gate = Gate.create(path.get(index), next);
      if (gate == null) return false;
      gates[index] = gate;
      points[index] = gate.initialPoint();
    }
    double previousLength = Double.POSITIVE_INFINITY;
    for (int pass = 0; pass < 32; pass++) {
      Vec3 source = closest(front, back, points[0]);
      for (int index = 0; index < count; index++) {
        var gate = gates[index];
        Vec3 before = index == 0 ? source : gates[index - 1].transform().point(points[index - 1]);
        Vec3 after = index + 1 == count
            ? before : gate.transform().inversePoint(points[index + 1]);
        points[index] = gate.closestPoint(before, after);
      }
      source = closest(front, back, points[0]);
      Vec3 position = source;
      double length = 0;
      for (int index = 0; index < count; index++) {
        length += position.distanceTo(points[index]);
        position = gates[index].transform().point(points[index]);
      }
      if (length <= reach + WITNESS_EPSILON) {
        if (traversable(path, source, points)) return true;
        // The nearest source can lie on the plane. Try a front-side source on the
        // same eye segment when the available boom length permits moving it.
        if (length < reach && frontDepth > 0 && !source.equals(front)) {
          double interval = source.distanceTo(front);
          var inside = source.lerp(front, Math.min(1, (reach - length) / interval));
          if (traversable(path, inside, points)) return true;
        }
      }
      if (previousLength - length < 1e-8) break;
      previousLength = length;
    }
    return false;
  }

  private static Vec3 closest(Vec3 from, Vec3 to, Vec3 point) {
    Vec3 movement = to.subtract(from);
    double length = movement.lengthSqr();
    return length == 0 ? from : from.add(movement.scale(
        Math.clamp(point.subtract(from).dot(movement) / length, 0, 1)));
  }

  private static boolean containsWitness(PortalEndpoint portal, Vec3 point) {
    if (portal.normal().y == 1) return PortalAperture.containsPassage(portal, point);
    // The closed wall aperture includes its edge, even when a round trip between
    // coordinate frames moves a fitted edge point by a few ulps.
    Vec3 local = point.subtract(portal.center());
    return Math.abs(local.dot(portal.right())) <= PortalAperture.passageHalfWidth(portal) + WITNESS_EPSILON
        && Math.abs(local.dot(portal.up())) <= PortalAperture.passageHalfHeight(portal) + WITNESS_EPSILON;
  }

  private record Gate(PortalTransform transform, double right, double up, Vec3 axis, Vec3 across,
      double width, double height, double min, double max, boolean ellipse) {
    private static final double GOLDEN_RATIO = (Math.sqrt(5) - 1) / 2;

    static @Nullable Gate create(PortalLink link, @Nullable PortalEndpoint next) {
      var transform = link.transform();
      var entrance = transform.entrance();
      var aperture = PortalAperture.limitingAperture(link);
      boolean ellipse = aperture.normal().y == 1;
      double inset = ellipse ? WITNESS_EPSILON : 0;
      double width = PortalAperture.passageHalfWidth(aperture) - inset;
      double height = PortalAperture.passageHalfHeight(aperture) - inset;
      Vec3 entranceRight = entrance.right(), entranceUp = entrance.up();
      double right = 1, up = 0, slope = 0, depth = 0;
      if (next != null) {
        Vec3 normal = transform.inverse().vector(next.normal());
        right = normal.dot(entranceRight);
        up = normal.dot(entranceUp);
        slope = Math.hypot(right, up);
        depth = PortalAperture.signedDistance(next, transform.exit().center());
        if (slope <= WITNESS_EPSILON) {
          if (depth < -PortalAperture.PLANE_EPSILON) return null;
          slope = 0;
          right = 1;
          up = 0;
        } else {
          right /= slope;
          up /= slope;
        }
      }
      double max = ellipse ? Math.hypot(width * right, height * up)
          : width * Math.abs(right) + height * Math.abs(up);
      double min = -max;
      if (slope > 0) {
        // The point at this exit is also the source of the next leg. Cut the aperture
        // to its front side so the search cannot prefer a shorter backface crossing.
        double front = depth + slope * max;
        if (front <= 0) return null;
        double clearance = Math.min(WITNESS_EPSILON, front / 2);
        min = Math.max(min, (clearance - depth) / slope);
      }
      Vec3 axis = entranceRight.scale(right).add(entranceUp.scale(up));
      Vec3 across = entranceUp.scale(right).subtract(entranceRight.scale(up));
      return new Gate(transform, right, up, axis, across, width, height, min, max, ellipse);
    }

    Vec3 initialPoint() {
      double x = Math.max(0, min);
      return point(x, clampAcross(x, 0));
    }

    Vec3 closestPoint(Vec3 from, Vec3 to) {
      var portal = transform.entrance();
      if (!ellipse && min == -max) return PortalAperture.shortestPathPoint(portal, from, to);

      var a = focus(from);
      var b = focus(to);
      double totalDepth = a.depth() + b.depth();
      double fraction = totalDepth == 0 ? .5 : a.depth() / totalDepth;
      double x = a.x() + (b.x() - a.x()) * fraction;
      double y = a.y() + (b.y() - a.y()) * fraction;
      if (x >= min && x <= max && y == clampAcross(x, y)) return point(x, y);

      // At fixed x, minimize along the chord analytically. The resulting length
      // is convex in x, including when the optimum lies on an aperture edge.
      double low = min, high = max;
      double left = high - (high - low) * GOLDEN_RATIO;
      double right = low + (high - low) * GOLDEN_RATIO;
      double leftLength = length(left, a, b), rightLength = length(right, a, b);
      for (int pass = 0; pass < 40 && high - low > 1e-9; pass++) {
        if (leftLength <= rightLength) {
          high = right;
          right = left;
          rightLength = leftLength;
          left = high - (high - low) * GOLDEN_RATIO;
          leftLength = length(left, a, b);
        } else {
          low = left;
          left = right;
          leftLength = rightLength;
          right = low + (high - low) * GOLDEN_RATIO;
          rightLength = length(right, a, b);
        }
      }
      x = leftLength <= rightLength ? left : right;
      double length = Math.min(leftLength, rightLength);
      if (length(min, a, b) <= length) x = min;
      if (length(max, a, b) < length(x, a, b)) x = max;
      return point(x, closestAcross(x, a, b));
    }

    private Focus focus(Vec3 point) {
      var portal = transform.entrance();
      Vec3 relative = point.subtract(portal.center());
      return new Focus(relative.dot(axis), relative.dot(across), Math.abs(relative.dot(portal.normal())));
    }

    private Vec3 point(double x, double y) {
      return transform.entrance().center().add(axis.scale(x)).add(across.scale(y));
    }

    private double length(double x, Focus a, Focus b) {
      double y = closestAcross(x, a, b);
      return a.distance(x, y) + b.distance(x, y);
    }

    private double closestAcross(double x, Focus a, Focus b) {
      double first = Math.hypot(a.x() - x, a.depth());
      double second = Math.hypot(b.x() - x, b.depth());
      double sum = first + second;
      double fraction = sum == 0 ? .5 : first / sum;
      return clampAcross(x, a.y() + (b.y() - a.y()) * fraction);
    }

    private double clampAcross(double x, double y) {
      if (ellipse) {
        double fraction = x / max;
        double center = right * up * (height * height - width * width) / max * fraction;
        double radius = width * height / max * Math.sqrt(Math.max(0, 1 - fraction * fraction));
        return Math.clamp(y, center - radius, center + radius);
      }
      double low = Double.NEGATIVE_INFINITY, high = Double.POSITIVE_INFINITY;
      if (up != 0) {
        double center = x * right / up, radius = width / Math.abs(up);
        low = center - radius;
        high = center + radius;
      }
      if (right != 0) {
        double center = -x * up / right, radius = height / Math.abs(right);
        low = Math.max(low, center - radius);
        high = Math.min(high, center + radius);
      }
      return Math.max(low, Math.min(high, y));
    }
  }

  private record Focus(double x, double y, double depth) {
    double distance(double x, double y) {
      double dx = this.x - x, dy = this.y - y;
      return Math.sqrt(dx * dx + dy * dy + depth * depth);
    }
  }
}
