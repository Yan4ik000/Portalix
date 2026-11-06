package dev.portalix.portal.camera;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalRoutes;
import dev.portalix.portal.geometry.PortalAperture;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PortalCameraOrbit {
  private static final double CLEARANCE = 1e-4;
  private static final double RECOVERY_RATE = 12;
  private final PortalCameraReorientation orientation = new PortalCameraReorientation(true);
  private final List<PortalLink> frame = new ArrayList<>();
  private boolean initialized;
  private Vec3 position;
  private Vec3 previousInput;
  private final List<Leg> transit = new ArrayList<>();
  private int nextLeg;

  public void initialize(
      Quaternionf bodyOrientation, Vec3 cameraPosition, List<PortalLink> initialFrame) {
    frame.clear();
    for (var link : initialFrame) PortalRoutes.append(frame, link);
    var observer = new Quaternionf(bodyOrientation);
    for (var link : frame) observer.premul(link.transform().rotation());
    orientation.resetView(observer);
    position = cameraPosition;
    previousInput = cameraPosition == null ? null : inBody(cameraPosition, frame);
    transit.clear();
    nextLeg = 0;
    initialized = true;
  }

  public void clear() {
    frame.clear();
    initialized = false;
    position = null;
    previousInput = null;
    transit.clear();
    nextLeg = 0;
  }

  public boolean active() {
    return initialized;
  }

  public boolean reorienting() {
    return initialized && orientation.active();
  }

  public void bodyCrossed(PortalLink link) {
    if (!active()) return;
    var rebased = new ArrayList<PortalLink>();
    PortalRoutes.append(rebased, link.inverse());
    for (var previous : frame) PortalRoutes.append(rebased, previous);
    frame.clear();
    frame.addAll(rebased);
    if (previousInput != null) previousInput = link.transform().point(previousInput);
  }

  public void advance(float seconds) {
    if (active()) orientation.update(seconds);
  }

  public void mouse(double yawDegrees, double pitchDegrees) {
    orientation.mouse(yawDegrees, pitchDegrees);
  }

  public void lookBody(float yaw, float pitch) {
    var observer = PortalLookAngles.rotation(yaw, pitch);
    for (var link : frame) observer.premul(link.transform().rotation());
    orientation.look(observer);
  }

  public Quaternionf bodyOrientation() {
    return inBody(orientation.orientation(), frame);
  }

  public PortalLookAngles bodyAngles() {
    return PortalLookAngles.from(inBody(orientation.commandOrientation(), frame));
  }

  public PortalThirdPersonCamera.Pose resolve(
      PortalThirdPersonCamera.Target target,
      Quaternionf bodyRenderOrientation,
      Vec3 bob,
      PortalCameraCorrections history,
      double time,
      double distance,
      List<PortalLink> links,
      PortalCameraCollision.Query query,
      float seconds) {
    Vec3 focus = target.eye().add(history.eyeCorrection(time)).add(bob);
    if (!links.containsAll(frame)
        || transit.stream()
            .anyMatch(leg -> leg.crossing() != null && !links.contains(leg.crossing())))
      initialize(bodyOrientation(), null, List.of());
    var backwards = bodyRenderOrientation.transform(new Vector3f(0, 0, 1));
    Vec3 input = focus.add(backwards.x * distance, backwards.y * distance, backwards.z * distance);
    double inputMovement = previousInput == null ? 0 : input.distanceTo(previousInput);
    previousInput = input;
    var desired =
        PortalThirdPersonCamera.resolve(
            target, bodyRenderOrientation, bob, history, time, distance, links, query);
    if (position == null) {
      for (var link : changes(frame, desired.path()))
        orientation.teleport(new Matrix3f().set(link.transform().rotation()));
      frame.clear();
      frame.addAll(desired.path());
      position = desired.position();
      previousInput = input;
      return desired;
    }
    var required = changes(frame, desired.path());
    Vec3 goal = inFrame(inBody(desired.position(), desired.path()), frame);
    Vec3 directMovement = goal.subtract(position);
    var direct =
        PortalCameraCollision.solve(position, directMovement, directMovement.length(), links, query);
    var rendered = new Quaternionf(bodyRenderOrientation);
    for (var link : frame) rendered.premul(link.transform().rotation());
    if (direct.links().equals(required)
        && direct.position().distanceToSqr(desired.position()) < 1e-12) {
      transit.clear();
      nextLeg = 0;
      double length = directMovement.length();
      move(
          directMovement,
          Math.min(length, budget(inputMovement, length, seconds)),
          links,
          query,
          rendered);
      return new PortalThirdPersonCamera.Pose(
          position, rendered, List.copyOf(frame), desired.pivot(), desired.pivotPath());
    }

    if (!transit.isEmpty()) {
      transit.set(transit.size() - 1, new Leg(desired.position(), null));
      var remaining = transit.subList(nextLeg, transit.size());
      var crossings = remaining.stream().map(Leg::crossing).filter(Objects::nonNull).toList();
      if (!crossings.equals(required) || !clearRoute(position, remaining, links, query)) {
        transit.clear();
        nextLeg = 0;
      }
    }
    if (transit.isEmpty()) {
      var apertureRoute = route(position, required, desired.position(), links, query);
      var toPivot = changes(frame, desired.pivotPath());
      var fromPivot = changes(desired.pivotPath(), desired.path());
      if (!clearRoute(position, apertureRoute, links, query)
          && toPivot.size() + fromPivot.size() == required.size()) {
        transit.addAll(route(position, toPivot, desired.pivot(), links, query));
        transit.addAll(route(desired.pivot(), fromPivot, desired.position(), links, query));
      } else transit.addAll(apertureRoute);
    }

    double budget =
        budget(inputMovement, routeLength(transit.subList(nextLeg, transit.size())), seconds);
    while (nextLeg < transit.size()) {
      var leg = transit.get(nextLeg);
      Vec3 movement = leg.target().subtract(position);
      double requested = movement.length();
      if (requested <= 1e-9) {
        nextLeg++;
        continue;
      }
      double travel = Math.min(requested, budget);
      if (travel <= 0) break;
      var actual = move(movement, travel, links, query, rendered);
      budget = Math.max(0, budget - actual.distance());
      var expected = leg.crossing() == null ? List.<PortalLink>of() : List.of(leg.crossing());
      if (leg.crossing() != null && actual.links().equals(expected))
        transit.set(nextLeg, new Leg(leg.crossing().transform().point(leg.target()), null));
      if (!actual.links().isEmpty() && !actual.links().equals(expected)) {
        transit.clear();
        nextLeg = 0;
        break;
      }
      if (actual.distance() < requested - 1e-8 || !actual.links().equals(expected)) break;
      nextLeg++;
    }
    if (nextLeg == transit.size()) {
      transit.clear();
      nextLeg = 0;
    }
    return new PortalThirdPersonCamera.Pose(
        position, rendered, List.copyOf(frame), desired.pivot(), desired.pivotPath());
  }

  private PortalCameraCollision.Result move(
      Vec3 direction,
      double distance,
      List<PortalLink> links,
      PortalCameraCollision.Query query,
      Quaternionf rendered) {
    var actual = PortalCameraCollision.solve(position, direction, distance, links, query);
    position = actual.position();
    for (var link : actual.links()) {
      PortalRoutes.append(frame, link);
      orientation.teleport(new Matrix3f().set(link.transform().rotation()));
      rendered.premul(link.transform().rotation());
    }
    return actual;
  }

  private static double budget(double inputMovement, double remaining, float seconds) {
    return inputMovement + remaining * -Math.expm1(-RECOVERY_RATE * Math.max(0, seconds));
  }

  private List<Leg> route(
      Vec3 start,
      List<PortalLink> required,
      Vec3 destination,
      List<PortalLink> links,
      PortalCameraCollision.Query query) {
    var route = new ArrayList<Leg>();
    Vec3 from = start;
    for (int index = 0; index < required.size(); index++) {
      var crossing = required.get(index);
      Vec3 target = destination;
      for (int remaining = required.size() - 1; remaining >= index; remaining--)
        target = required.get(remaining).transform().inverse().point(target);
      var portal = crossing.entrance().endpoint();
      Vec3 opening = fit(portal, PortalAperture.shortestPathPoint(portal, from, target));
      opening =
          crossing
              .transform()
              .inverse()
              .point(fit(crossing.exit().endpoint(), crossing.transform().point(opening)));
      Vec3 approach = opening.add(portal.normal().scale(radius(portal.normal()) + CLEARANCE));
      Vec3 through = opening.subtract(portal.normal().scale(CLEARANCE));
      route.add(new Leg(approach, null));
      route.add(new Leg(through, crossing));
      var exit = crossing.exit().endpoint();
      from =
          crossing
              .transform()
              .point(opening)
              .add(exit.normal().scale(radius(exit.normal()) + CLEARANCE));
      route.add(new Leg(from, null));
    }
    route.addAll(localRoute(from, destination, links, query));
    return route;
  }

  private static List<Leg> localRoute(
      Vec3 start, Vec3 destination, List<PortalLink> links, PortalCameraCollision.Query query) {
    var direct = List.of(new Leg(destination, null));
    if (clearRoute(start, direct, links, query)) return direct;
    var departures = clearances(start, links);
    var arrivals = clearances(destination, links);
    List<Leg> best = direct;
    double bestLength = Double.POSITIVE_INFINITY;
    for (var departure : departures) {
      for (var arrival : arrivals) {
        var candidate = new ArrayList<Leg>();
        if (!departure.equals(start)) candidate.add(new Leg(departure, null));
        if (!arrival.equals(destination)) candidate.add(new Leg(arrival, null));
        candidate.add(new Leg(destination, null));
        double length = routeLength(start, candidate);
        if (length < bestLength && clearRoute(start, candidate, links, query)) {
          best = candidate;
          bestLength = length;
        }
      }
    }
    return best;
  }

  private static List<Vec3> clearances(Vec3 point, List<PortalLink> links) {
    var result = new ArrayList<Vec3>();
    result.add(point);
    for (var link : links) {
      var portal = link.entrance().endpoint();
      double depth = PortalAperture.signedDistance(portal, point);
      double clearance = radius(portal.normal()) + CLEARANCE;
      if (depth >= 0 && depth < clearance && PortalAperture.containsPassage(portal, point))
        result.add(point.add(portal.normal().scale(clearance - depth)));
    }
    return result;
  }

  private double routeLength(List<Leg> route) {
    return routeLength(position, route);
  }

  private static double routeLength(Vec3 start, List<Leg> route) {
    double length = 0;
    Vec3 from = start;
    for (var leg : route) {
      length += from.distanceTo(leg.target());
      from = leg.crossing() == null ? leg.target() : leg.crossing().transform().point(leg.target());
    }
    return length;
  }

  private static Vec3 fit(PortalEndpoint portal, Vec3 point) {
    Vec3 relative = point.subtract(portal.center());
    double x = relative.dot(portal.right()), y = relative.dot(portal.up());
    double halfWidth = PortalAperture.passageHalfWidth(portal);
    double halfHeight = PortalAperture.passageHalfHeight(portal);
    if (portal.normal().y == 1) {
      double margin = Math.sqrt(2) * PortalCameraCollision.RADIUS + CLEARANCE;
      double limit = Math.max(0, 1 - margin / Math.min(halfWidth, halfHeight));
      double metric = Math.hypot(x / halfWidth, y / halfHeight);
      if (metric > limit) {
        x *= limit / metric;
        y *= limit / metric;
      }
    } else {
      double width = Math.max(0, halfWidth - radius(portal.right()) - CLEARANCE);
      double height = Math.max(0, halfHeight - radius(portal.up()) - CLEARANCE);
      x = Math.clamp(x, -width, width);
      y = Math.clamp(y, -height, height);
    }
    return portal.center().add(portal.right().scale(x)).add(portal.up().scale(y));
  }

  private static boolean clearRoute(
      Vec3 start, List<Leg> route, List<PortalLink> links, PortalCameraCollision.Query query) {
    Vec3 position = start;
    for (var leg : route) {
      Vec3 movement = leg.target().subtract(position);
      var actual = PortalCameraCollision.solve(position, movement, movement.length(), links, query);
      var expected = leg.crossing() == null ? List.<PortalLink>of() : List.of(leg.crossing());
      if (!actual.links().equals(expected)
          || actual.distance() < movement.length() - 1e-8) return false;
      position = actual.position();
    }
    return true;
  }

  private static double radius(Vec3 axis) {
    return PortalCameraCollision.RADIUS * (Math.abs(axis.x) + Math.abs(axis.y) + Math.abs(axis.z));
  }

  private static List<PortalLink> changes(List<PortalLink> before, List<PortalLink> after) {
    var result = new ArrayList<PortalLink>();
    for (var link : before.reversed()) PortalRoutes.append(result, link.inverse());
    for (var link : after) PortalRoutes.append(result, link);
    return result;
  }

  private static Vec3 inBody(Vec3 point, List<PortalLink> frame) {
    for (var link : frame.reversed()) point = link.transform().inverse().point(point);
    return point;
  }

  private static Vec3 inFrame(Vec3 point, List<PortalLink> frame) {
    for (var link : frame) point = link.transform().point(point);
    return point;
  }

  private static Quaternionf inBody(Quaternionf rotation, List<PortalLink> frame) {
    for (var link : frame.reversed()) rotation.premul(link.transform().rotation().conjugate());
    return rotation;
  }

  private record Leg(Vec3 target, PortalLink crossing) {}

}
