package dev.portalix.portal.audio;

import com.google.common.base.Suppliers;
import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalRoutes;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.geometry.PortalTransform;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Supplier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PortalSoundPropagation {
  private static final double SURFACE_OFFSET = 1e-4;

  private PortalSoundPropagation() {}

  public static Routes prepare(List<PortalLink> links) {
    var openings = new ArrayList<AcousticLink>(links.size());
    for (var link : links) {
      var opening = Opening.aperture(link);
      var transform = link.transform();
      var bounds = opening.bounds();
      openings.add(new AcousticLink(link, opening, transform, bounds, transform.bounds(bounds)));
    }
    var routes = new ArrayList<Route>();
    for (var first : openings) {
      var inverse = first.link.inverse();
      var inverseTransform = first.transform.inverse();
      var exit = first.link.exit().endpoint();
      routes.add(new Route(List.of(first.link), first.opening, null, first.transform, null,
          first.entranceBounds, first.exitBounds, 0));
      for (var second : openings) {
        if (PortalRoutes.sameIdentity(first.link, second.link)
            || PortalRoutes.sameIdentity(inverse, second.link)) continue;
        var entrance = second.link.entrance().endpoint();
        var outgoing = first.opening.clip(
            inverseTransform.point(entrance.center()), inverseTransform.vector(entrance.normal()));
        if (outgoing == null) continue;
        var incoming = second.opening.clip(exit.center(), exit.normal());
        if (incoming != null)
          routes.add(new Route(
              List.of(first.link, second.link), outgoing, incoming, first.transform, second.transform,
              first.entranceBounds, second.exitBounds, distance(first.exitBounds, second.entranceBounds)));
      }
    }
    return new Routes(List.copyOf(routes));
  }

  private static double distance(AABB first, AABB second) {
    double x = Math.max(0, Math.max(first.minX - second.maxX, second.minX - first.maxX));
    double y = Math.max(0, Math.max(first.minY - second.maxY, second.minY - first.maxY));
    double z = Math.max(0, Math.max(first.minZ - second.maxZ, second.minZ - first.maxZ));
    return Math.sqrt(x * x + y * y + z * z);
  }

  private record AcousticLink(PortalLink link, Opening opening, PortalTransform transform,
      AABB entranceBounds, AABB exitBounds) {}

  public static final class Routes {
    private final List<Route> routes;
    private final Supplier<RouteIndex> index;

    private Routes(List<Route> routes) {
      this.routes = routes;
      index = Suppliers.memoize(() -> RouteIndex.build(routes, 0, routes.size()));
    }

    public List<Observer> observers(Vec3 listener, double range) {
      var observers = new ArrayList<Observer>();
      for (var route : routes) {
        var exit = route.links.getLast().exit().endpoint();
        Vec3 emitter = outward(exit, exit.center());
        if (!route.frontFacing(listener, emitter)) continue;
        var opening = route.second == null ? route.first : route.second;
        double radius = Math.hypot(opening.halfWidth, opening.halfHeight);
        Vec3 image = route.image(emitter);
        if (listener.distanceTo(image) - radius > range) continue;
        if (route.bends(listener, emitter, image).distance - radius > range) continue;
        Vec3 position = route.firstTransform.point(listener);
        if (route.secondTransform != null) position = route.secondTransform.point(position);
        observers.add(new Observer(route.links, position, exit));
      }
      return List.copyOf(observers);
    }

    public double delayDistanceSquared(Vec3 listener, Vec3 emitter) {
      return distanceSquared(listener, emitter, Double.NEGATIVE_INFINITY);
    }

    public boolean withinDistance(Vec3 listener, Vec3 emitter, double range) {
      double limit = range * range;
      return distanceSquared(listener, emitter, limit) <= limit;
    }

    public boolean closerThan(Vec3 listener, Vec3 emitter, double range) {
      double limit = range * range;
      return range > 0 && distanceSquared(listener, emitter, Math.nextDown(limit)) < limit;
    }

    private double distanceSquared(Vec3 listener, Vec3 emitter, double stopAtSquared) {
      double distance = listener.distanceTo(emitter);
      double squared = distance * distance;
      if (squared <= stopAtSquared) return squared;
      // Keep the same tie-breaking as the gain-based delay search.
      double bestGain = delayGain(distance);
      for (var route : routes) {
        if (!route.frontFacing(listener, emitter)) continue;
        Vec3 image = route.image(emitter);
        if (delayGain(listener.distanceTo(image)) <= bestGain) continue;
        double candidate = route.bends(listener, emitter, image).distance;
        double gain = delayGain(candidate);
        if (gain > bestGain) {
          bestGain = gain;
          squared = candidate * candidate;
          if (squared <= stopAtSquared) return squared;
        }
      }
      return squared;
    }

    private static double delayGain(double distance) {
      return 1 / (1 + distance);
    }

    public Path findPath(
        Vec3 listener,
        Vec3 emitter,
        DoubleUnaryOperator distanceGain,
        double sourceRadius,
        Transmission transmission) {
      double distance = listener.distanceTo(emitter);
      double directGain = Math.max(0, distanceGain.applyAsDouble(distance));
      double directTransmission = directGain > 0 ? transmission.gain(listener, emitter, sourceRadius) : 1;
      var best = new Path(List.of(), emitter, directGain, directTransmission, distance);
      if (routes.isEmpty()) return best;
      var search = new PathSearch(listener, emitter, distanceGain, sourceRadius, transmission, best);
      search.visit(index.get(), routes);
      return search.best;
    }
  }

  private static final class PathSearch {
    private final Vec3 listener;
    private final Vec3 emitter;
    private final DoubleUnaryOperator distanceGain;
    private final double sourceRadius;
    private final Transmission transmission;
    private final double farGain;
    private Path best;

    PathSearch(Vec3 listener, Vec3 emitter, DoubleUnaryOperator distanceGain,
        double sourceRadius, Transmission transmission, Path best) {
      this.listener = listener;
      this.emitter = emitter;
      this.distanceGain = distanceGain;
      this.sourceRadius = sourceRadius;
      this.transmission = transmission;
      this.best = best;
      // Source's low-gain tail can fall below its far-distance floor.
      farGain = Math.max(0, distanceGain.applyAsDouble(Double.POSITIVE_INFINITY));
    }

    void visit(RouteIndex index, List<Route> routes) {
      double shortest = Math.sqrt(index.entranceBounds.distanceToSqr(listener))
          + index.middleDistance + Math.sqrt(index.exitBounds.distanceToSqr(emitter));
      if (Math.max(farGain, distanceGain.applyAsDouble(shortest)) <= best.gain()) return;
      if (index.left != null) {
        visit(index.left, routes);
        visit(index.right, routes);
        return;
      }
      for (int i = index.from; i < index.to; i++) {
        var route = routes.get(i);
        if (!route.frontFacing(listener, emitter)) continue;
        Vec3 image = route.image(emitter);
        double upperGain = Math.max(farGain, distanceGain.applyAsDouble(listener.distanceTo(image)));
        if (upperGain <= best.gain()) continue;
        Path path = route.path(listener, emitter, image, distanceGain, sourceRadius, transmission, best.gain());
        if (path != null) best = path;
      }
    }
  }

  private static final class RouteIndex {
    private final int from;
    private final int to;
    private final AABB entranceBounds;
    private final AABB exitBounds;
    private final double middleDistance;
    private final RouteIndex left;
    private final RouteIndex right;

    private RouteIndex(int from, int to, AABB entranceBounds, AABB exitBounds,
        double middleDistance, RouteIndex left, RouteIndex right) {
      this.from = from;
      this.to = to;
      this.entranceBounds = entranceBounds;
      this.exitBounds = exitBounds;
      this.middleDistance = middleDistance;
      this.left = left;
      this.right = right;
    }

    static RouteIndex build(List<Route> routes, int from, int to) {
      if (to - from > 8) {
        int middle = (from + to) >>> 1;
        var left = build(routes, from, middle);
        var right = build(routes, middle, to);
        return new RouteIndex(from, to, left.entranceBounds.minmax(right.entranceBounds),
            left.exitBounds.minmax(right.exitBounds), Math.min(left.middleDistance, right.middleDistance),
            left, right);
      }
      var first = routes.get(from);
      var entrances = first.entranceBounds;
      var exits = first.exitBounds;
      double shortest = first.middleDistance;
      for (int i = from + 1; i < to; i++) {
        var route = routes.get(i);
        entrances = entrances.minmax(route.entranceBounds);
        exits = exits.minmax(route.exitBounds);
        shortest = Math.min(shortest, route.middleDistance);
      }
      return new RouteIndex(from, to, entrances, exits, shortest, null, null);
    }
  }

  private record Route(
      List<PortalLink> links,
      Opening first,
      Opening second,
      PortalTransform firstTransform,
      PortalTransform secondTransform,
      AABB entranceBounds,
      AABB exitBounds,
      double middleDistance) {
    boolean frontFacing(Vec3 listener, Vec3 emitter) {
      return PortalAperture.signedDistance(first.portal, listener) >= -PortalAperture.PLANE_EPSILON
          && PortalAperture.signedDistance(links.getLast().exit().endpoint(), emitter)
              >= -PortalAperture.PLANE_EPSILON;
    }

    Vec3 image(Vec3 emitter) {
      return firstTransform.inversePoint(
          secondTransform == null ? emitter : secondTransform.inversePoint(emitter));
    }

    Bends bends(Vec3 listener, Vec3 emitter, Vec3 image) {
      Vec3 firstPoint = first.shortestPoint(listener, image);
      Vec3 firstExit = firstTransform.point(firstPoint);
      Vec3 secondPoint = null;
      if (second != null) {
        Vec3 lastImage = secondTransform.inversePoint(emitter);
        secondPoint = second.shortestPoint(firstExit, lastImage);
        double previousLength = Double.POSITIVE_INFINITY;
        // Each update minimizes one bend of the convex path through the two openings.
        for (int pass = 0; pass < 32; pass++) {
          firstPoint = first.shortestPoint(listener, firstTransform.inversePoint(secondPoint));
          firstExit = firstTransform.point(firstPoint);
          secondPoint = second.shortestPoint(firstExit, lastImage);
          double length = listener.distanceTo(firstPoint)
              + firstExit.distanceTo(secondPoint)
              + secondPoint.distanceTo(lastImage);
          if (previousLength - length <= 1e-8) break;
          previousLength = length;
        }
      }
      double distance = listener.distanceTo(firstPoint);
      Vec3 lastExit = firstExit;
      if (secondPoint != null) {
        distance += firstExit.distanceTo(secondPoint);
        lastExit = secondTransform.point(secondPoint);
      }
      distance += lastExit.distanceTo(emitter);
      return new Bends(firstPoint, firstExit, secondPoint, lastExit, distance);
    }

    Path path(
        Vec3 listener,
        Vec3 emitter,
        Vec3 image,
        DoubleUnaryOperator distanceGain,
        double sourceRadius,
        Transmission transmission,
        double bestGain) {
      var bends = bends(listener, emitter, image);
      double attenuation = Math.max(0, distanceGain.applyAsDouble(bends.distance));
      if (attenuation <= bestGain) return null;
      double gain = transmission.gain(listener, outward(first.portal, bends.firstPoint), 0);
      if (attenuation * gain <= bestGain) return null;
      if (bends.secondPoint != null) {
        gain *= transmission.gain(
            outward(links.getFirst().exit().endpoint(), bends.firstExit),
            outward(second.portal, bends.secondPoint), 0);
        if (attenuation * gain <= bestGain) return null;
      }
      gain *= transmission.gain(
          outward(links.getLast().exit().endpoint(), bends.lastExit), emitter, sourceRadius);
      if (!(attenuation * gain > bestGain)) return null;
      Vec3 direction = bends.firstPoint.subtract(listener);
      if (direction.lengthSqr() < 1e-12)
        direction = (bends.secondPoint == null ? image : firstTransform.inversePoint(bends.secondPoint))
            .subtract(listener);
      if (direction.lengthSqr() < 1e-12) direction = first.portal.normal().scale(-1);
      Vec3 position = listener.add(direction.normalize().scale(bends.distance));
      return new Path(links, position, attenuation, gain, bends.distance);
    }
  }

  private record Bends(Vec3 firstPoint, Vec3 firstExit, Vec3 secondPoint, Vec3 lastExit, double distance) {}

  private record Opening(
      PortalEndpoint portal, List<Vec3> vertices, BoundaryClip clipping, double halfWidth, double halfHeight) {
    AABB bounds() {
      Vec3 right = portal.right().scale(halfWidth), up = portal.up().scale(halfHeight);
      Vec3 center = portal.center();
      double x = Math.abs(right.x) + Math.abs(up.x);
      double y = Math.abs(right.y) + Math.abs(up.y);
      double z = Math.abs(right.z) + Math.abs(up.z);
      return new AABB(center.x - x, center.y - y, center.z - z,
          center.x + x, center.y + y, center.z + z).inflate(PortalAperture.PLANE_EPSILON);
    }

    static Opening aperture(PortalLink link) {
      var portal = link.entrance().endpoint();
      var aperture = PortalAperture.limitingAperture(link);
      if (aperture.normal().y == 1) {
        return new Opening(portal, List.of(), null,
            PortalAperture.passageHalfWidth(aperture), PortalAperture.passageHalfHeight(aperture));
      }
      Vec3 right = portal.right().scale(portal.width() / 2);
      Vec3 up = portal.up().scale(portal.height() / 2);
      Vec3 center = portal.center();
      return new Opening(portal, List.of(
          center.subtract(right).subtract(up), center.add(right).subtract(up),
          center.add(right).add(up), center.subtract(right).add(up)), null, portal.width() / 2, portal.height() / 2);
    }

    Opening clip(Vec3 center, Vec3 normal) {
      if (vertices.isEmpty()) {
        double a = portal.right().dot(normal) * halfWidth;
        double b = portal.up().dot(normal) * halfHeight;
        double distance = portal.center().subtract(center).dot(normal);
        double extent = Math.hypot(a, b);
        if (distance >= extent) return this;
        if (distance <= -extent) return null;
        double x = -distance * a / (extent * extent);
        double y = -distance * b / (extent * extent);
        double halfChord = Math.sqrt(Math.max(0, 1 - distance * distance / (extent * extent)));
        Vec3 midpoint = portal.center().add(portal.right().scale(x * halfWidth))
            .add(portal.up().scale(y * halfHeight));
        Vec3 offset = portal.right().scale(-b * halfWidth * halfChord / extent)
            .add(portal.up().scale(a * halfHeight * halfChord / extent));
        return new Opening(portal, vertices,
            new BoundaryClip(center, normal, midpoint.subtract(offset), midpoint.add(offset)), halfWidth, halfHeight);
      }
      var clipped = new ArrayList<Vec3>();
      Vec3 previous = vertices.getLast();
      double previousDistance = previous.subtract(center).dot(normal);
      for (var vertex : vertices) {
        double distance = vertex.subtract(center).dot(normal);
        if ((previousDistance < 0) != (distance < 0))
          clipped.add(previous.lerp(vertex, previousDistance / (previousDistance - distance)));
        if (distance >= 0) clipped.add(vertex);
        previous = vertex;
        previousDistance = distance;
      }
      double area = 0;
      previous = clipped.isEmpty() ? null : clipped.getLast();
      for (var vertex : clipped) {
        area += previous.subtract(portal.center()).cross(vertex.subtract(portal.center()))
            .dot(portal.normal());
        previous = vertex;
      }
      return area <= 1e-12 ? null : new Opening(portal, List.copyOf(clipped), null, halfWidth, halfHeight);
    }

    Vec3 shortestPoint(Vec3 from, Vec3 to) {
      double firstDistance = Math.abs(PortalAperture.signedDistance(portal, from));
      double secondDistance = Math.abs(PortalAperture.signedDistance(portal, to));
      double sum = firstDistance + secondDistance;
      Vec3 point = from.lerp(to, sum > 0 ? firstDistance / sum : .5);
      point = point.subtract(portal.normal().scale(PortalAperture.signedDistance(portal, point)));
      if (vertices.isEmpty()) {
        Vec3 candidate = ellipsePoint(from, to, point);
        return clipping == null || candidate.subtract(clipping.center).dot(clipping.normal) >= 0
            ? candidate : shortestOnEdge(from, to, clipping.first, clipping.last);
      }
      boolean inside = true;
      Vec3 previous = vertices.getLast();
      for (var vertex : vertices) {
        if (vertex.subtract(previous).cross(point.subtract(previous)).dot(portal.normal()) < -1e-10) {
          inside = false;
          break;
        }
        previous = vertex;
      }
      if (inside) return point;
      Vec3 best = null;
      double shortest = Double.POSITIVE_INFINITY;
      previous = vertices.getLast();
      for (var vertex : vertices) {
        if (previous.distanceToSqr(vertex) > 0) {
          Vec3 candidate = shortestOnEdge(from, to, previous, vertex);
          double distance = from.distanceTo(candidate) + candidate.distanceTo(to);
          if (distance < shortest) {
            best = candidate;
            shortest = distance;
          }
        }
        previous = vertex;
      }
      return best;
    }

    private Vec3 ellipsePoint(Vec3 from, Vec3 to, Vec3 planeMinimum) {
      Vec3 rightAxis = portal.right(), upAxis = portal.up();
      Vec3 relative = planeMinimum.subtract(portal.center());
      double x = relative.dot(rightAxis) / halfWidth;
      double y = relative.dot(upAxis) / halfHeight;
      double radius = Math.hypot(x, y);
      if (radius <= 1) return planeMinimum;
      Vec3 a = from.subtract(portal.center()), b = to.subtract(portal.center());
      double ax = a.dot(rightAxis), ay = a.dot(upAxis), az = a.dot(portal.normal());
      double bx = b.dot(rightAxis), by = b.dot(upAxis), bz = b.dot(portal.normal());
      DoubleUnaryOperator length = theta -> {
        double dx = halfWidth * Math.cos(theta), dy = halfHeight * Math.sin(theta);
        return Math.sqrt((dx - ax) * (dx - ax) + (dy - ay) * (dy - ay) + az * az)
            + Math.sqrt((dx - bx) * (dx - bx) + (dy - by) * (dy - by) + bz * bz);
      };
      double angle = Math.atan2(y, x);
      double span = Math.acos(1 / radius);
      double left = angle - span, right = angle + span;
      double ratio = (Math.sqrt(5) - 1) / 2;
      double first = right - (right - left) * ratio;
      double second = left + (right - left) * ratio;
      double firstLength = length.applyAsDouble(first);
      double secondLength = length.applyAsDouble(second);
      // Convexity restricts the minimum to the arc visible from the plane minimum.
      for (int pass = 0; pass < 48 && right - left > 1e-9; pass++) {
        if (firstLength <= secondLength) {
          right = second;
          second = first;
          secondLength = firstLength;
          first = right - (right - left) * ratio;
          firstLength = length.applyAsDouble(first);
        } else {
          left = first;
          first = second;
          firstLength = secondLength;
          second = left + (right - left) * ratio;
          secondLength = length.applyAsDouble(second);
        }
      }
      double theta = (left + right) / 2;
      return portal.center().add(rightAxis.scale(halfWidth * Math.cos(theta)))
          .add(upAxis.scale(halfHeight * Math.sin(theta)));
    }

    private static Vec3 shortestOnEdge(Vec3 from, Vec3 to, Vec3 first, Vec3 last) {
      Vec3 edge = last.subtract(first);
      double length = edge.length();
      if (length == 0) return first;
      Vec3 axis = edge.scale(1 / length);
      Vec3 a = from.subtract(first), b = to.subtract(first);
      double x = a.dot(axis), y = b.dot(axis);
      double h = a.subtract(axis.scale(x)).length();
      double k = b.subtract(axis.scale(y)).length();
      double along = h + k > 0 ? (k * x + h * y) / (h + k) : (x + y) / 2;
      return first.add(axis.scale(Math.clamp(along, 0, length)));
    }
  }

  private record BoundaryClip(Vec3 center, Vec3 normal, Vec3 first, Vec3 last) {}

  private static Vec3 outward(PortalEndpoint portal, Vec3 point) {
    return point.add(portal.normal().scale(SURFACE_OFFSET));
  }

  @FunctionalInterface
  public interface Transmission {
    /** Fraction of the sound remaining after crossing the segment, from zero to one. */
    double gain(Vec3 start, Vec3 end, double sourceRadius);
  }

  public record Path(
      List<PortalLink> links, Vec3 position, double distanceGain, double transmission, double distance) {
    public double gain() {
      return distanceGain * transmission;
    }
  }

  public record Observer(List<PortalLink> links, Vec3 position, PortalEndpoint exit) {}
}
