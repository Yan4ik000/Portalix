package dev.portalix.client.carry.prediction;

import dev.portalix.carry.CarryAnchor;
import dev.portalix.carry.CarryCollisionWorld;
import dev.portalix.carry.CarryFollowMotion;
import dev.portalix.carry.CarryMotionPath;
import dev.portalix.gun.GunFireTiming;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalRoutes;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class CarryHoldPrediction {
  private static final int MAX_PREDICTION_STEPS = 256;
  private final CarryCollisionWorld collisions;
  private final CarryFollowMotion motion;
  private final NavigableMap<Long, Step> steps = new TreeMap<>();
  private Sample baseline;
  private long confirmedTick;
  private double nextStart = Double.NaN;

  CarryHoldPrediction(CarryCollisionWorld collisions, long tick, Vec3 position, Vec3 velocity) {
    this.collisions = collisions;
    motion = new CarryFollowMotion(velocity.scale(1 / GunFireTiming.TICK_SECONDS));
    baseline = new Sample(position, Vec3.ZERO, List.of());
    confirmedTick = tick;
  }

  void advance(CarryAnchor.HoldTarget target, List<PortalLink> route, double now, double interval) {
    while (!steps.isEmpty() && steps.firstKey() <= confirmedTick && steps.firstEntry().getValue().end() <= now)
      baseline = steps.pollFirstEntry().getValue().sample(now);
    if (Double.isNaN(nextStart) || steps.isEmpty() && nextStart < now) nextStart = now;
    long tick = steps.isEmpty() ? confirmedTick : steps.lastKey();
    var input = new Input(target, route);
    // A local update supplies the aim for server steps starting within the next client tick.
    while (nextStart < now + 1 - 1e-9 && tick - confirmedTick < MAX_PREDICTION_STEPS) {
      step(++tick, input, nextStart, interval);
      nextStart += interval;
    }
  }

  boolean confirm(long tick, Vec3 position, Vec3 velocity, double now, double interval) {
    var prior = steps.floorEntry(tick);
    Step reference = prior == null ? null : prior.getValue();
    Step predicted = prior != null && prior.getKey() == tick ? reference : null;
    Vec3 referencePosition = reference == null ? baseline.position : reference.path.end();
    var referenceFrame = reference == null ? baseline.crossings : reference.crossings(1);
    var frame = referenceFrame;
    if (!frame.isEmpty()) {
      // A crossing packet rebases snapshots before the displayed path reaches the seam.
      Vec3 projectedPosition = position;
      Vec3 projectedVelocity = velocity;
      for (var link : frame) {
        var transform = link.transform();
        projectedPosition = transform.point(projectedPosition);
        projectedVelocity = transform.vector(projectedVelocity);
      }
      if (canCorrectWithinSpace(collisions, referencePosition, projectedPosition)) {
        position = projectedPosition;
        velocity = projectedVelocity;
      } else frame = List.of();
    }

    confirmedTick = tick;
    // A slower cadence can put whole steps ahead of the estimate during startup or a rate change.
    // Their inputs were sampled too early, so restart from the latest authoritative body.
    boolean staleCadence = predicted != null && interval > predicted.interval
        && (now - predicted.end()) * (1 / predicted.interval - 1 / interval) >= 1;
    boolean sameFrame = frame.equals(referenceFrame);
    if (!staleCadence && predicted != null && sameFrame
        && predicted.path.end().distanceToSqr(position) < 1e-12
        && predicted.path.movement(1).distanceToSqr(velocity) < 1e-12) return false;

    List<Step> unconfirmed = staleCadence ? List.of() : new ArrayList<>(steps.tailMap(tick, false).values());
    if (unconfirmed.isEmpty() && (staleCadence || predicted == null)) nextStart = now;
    steps.clear();
    baseline = new Sample(position, Vec3.ZERO, frame);
    Vec3 target = predicted != null && sameFrame ? predicted.trackedTarget : null;
    motion.reset(velocity.scale(1 / GunFireTiming.TICK_SECONDS), target == null ? null : position.subtract(target));
    for (var previous : unconfirmed) step(++tick, previous.input, previous.start, previous.interval);
    return true;
  }

  static boolean canCorrectWithinSpace(CarryCollisionWorld collisions, Vec3 from, Vec3 target) {
    double distance = from.distanceToSqr(target);
    // Two positions within reach of one holder bound a same-space reconciliation sweep.
    double reach = 2 * CarryAnchor.MAX_HOLDER_DISTANCE;
    if (distance > reach * reach) return false;
    if (distance < 1e-12) return true;
    var path = collisions.sweep(from, target).path();
    if (path.end().distanceToSqr(target) > 1e-12) return false;
    for (var node : path.nodes()) if (node.crossing() != null) return false;
    return true;
  }

  void crossed(PortalLink link) {
    baseline = new Sample(baseline.position, baseline.velocity, rebase(baseline.crossings, link));
    steps.replaceAll((tick, step) -> {
      var route = new ArrayList<>(step.input.route);
      route.add(link);
      return new Step(new Input(step.input.target, PortalRoutes.simplify(route)), step.path,
          rebase(step.frame, link), step.trackedTarget, step.start, step.interval);
    });
  }

  Sample sample(double now) {
    for (var step : steps.descendingMap().values()) if (step.start <= now) return step.sample(now);
    return baseline;
  }

  AABB bounds(AABB hull, double now) {
    Vec3 center = sample(now).position;
    AABB bounds = hull.move(center.subtract(hull.getCenter()));
    for (var step : steps.descendingMap().values()) {
      if (step.end() < now) break;
      bounds = bounds.minmax(step.path.bounds(hull));
    }
    return bounds;
  }

  private void step(long tick, Input input, double start, double interval) {
    var previous = steps.lastEntry();
    Vec3 position = previous == null ? baseline.position : previous.getValue().path.end();
    var frame = previous == null ? baseline.crossings : previous.getValue().crossings(1);
    var route = new ArrayList<>(input.route);
    route.addAll(frame);
    AABB hull = collisions.bounds();
    Vec3 desired = input.target.position(PortalRoutes.simplify(route), hull.move(position.subtract(hull.getCenter())));
    var path = motion.step(collisions, position, desired, GunFireTiming.TICK_SECONDS);
    steps.put(tick, new Step(input, path, frame, motion.trackedTarget(path.end()), start, interval));
  }

  private static List<PortalLink> rebase(List<PortalLink> frame, PortalLink link) {
    if (!frame.isEmpty() && PortalRoutes.sameIdentity(frame.getFirst(), link))
      return List.copyOf(frame.subList(1, frame.size()));
    var result = new ArrayList<>(frame);
    result.addFirst(link.inverse());
    return List.copyOf(result);
  }

  record Sample(Vec3 position, Vec3 velocity, List<PortalLink> crossings) {}

  private record Input(CarryAnchor.HoldTarget target, List<PortalLink> route) {}

  private record Step(Input input, CarryMotionPath path, List<PortalLink> frame,
                      @Nullable Vec3 trackedTarget, double start, double interval) {
    Sample sample(double now) {
      float partial = (float) Math.clamp((now - start) / interval, 0, 1);
      Vec3 velocity = now > end() ? Vec3.ZERO : path.movement(partial).scale(1 / interval);
      return new Sample(path.center(partial), velocity, crossings(partial));
    }

    List<PortalLink> crossings(float partial) {
      var crossed = path.crossings(partial);
      if (crossed.isEmpty()) return frame;
      var result = new ArrayList<>(frame);
      result.addAll(crossed);
      return PortalRoutes.simplify(result);
    }

    double end() {
      return start + interval;
    }
  }
}
