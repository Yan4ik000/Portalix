package dev.portalix.carry;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class CarryFollowMotion {
  private static final double RESPONSE = 18.0;
  private Vec3 velocity;
  private @Nullable Vec3 trackingError;
  private final CarryPathfinder path = new CarryPathfinder();
  private boolean detouring;

  public CarryFollowMotion(Vec3 initialVelocity) {
    this.velocity = initialVelocity;
  }

  public void reset(Vec3 actualVelocity) {
    reset(actualVelocity, null);
  }

  public void reset(Vec3 actualVelocity, @Nullable Vec3 error) {
    velocity = actualVelocity;
    trackingError = error;
    path.clear();
    detouring = false;
  }

  public @Nullable Vec3 trackedTarget(Vec3 position) {
    return trackingError == null ? null : position.subtract(trackingError);
  }

  public CarryMotionPath step(
      CarryCollisionWorld collisions, Vec3 current, Vec3 desired, double seconds) {
    if (seconds <= 0.0) return CarryMotionPath.stationary(current);
    Vec3 target = path.steeringTarget(collisions, current, desired);
    boolean nextDetouring = path.hasDetour();
    if (detouring || nextDetouring) trackingError = null;
    detouring = nextDetouring;
    if (nextDetouring) return followDetour(collisions, current, seconds);
    Vec3 error = trackingError == null ? current.subtract(target) : trackingError;
    Vec3 targetVelocity = trackingError == null ? Vec3.ZERO
        : target.subtract(current.subtract(error)).scale(1.0 / seconds);
    double decay = Math.exp(-RESPONSE * seconds);
    Vec3 next = target.add(error.scale(decay));
    Vec3 nextVelocity = targetVelocity.subtract(error.scale(RESPONSE * decay));
    var swept = collisions.sweep(current, next);
    velocity = swept.constrain(nextVelocity);
    trackingError = swept.path().end().subtract(swept.path().point(target));
    return swept.path();
  }

  private CarryMotionPath followDetour(
      CarryCollisionWorld collisions, Vec3 current, double seconds) {
    List<Vec3> route = path.remainingWaypoints();
    double distance = 0.0;
    Vec3 previous = current;
    for (Vec3 point : route) {
      distance += previous.distanceTo(point);
      previous = point;
    }
    if (distance <= 1.0E-12) {
      velocity = Vec3.ZERO;
      return CarryMotionPath.stationary(current);
    }

    double speed = velocity.length();
    double tangent = RESPONSE * distance - speed;
    double decay = Math.exp(-RESPONSE * seconds);
    double remaining = (distance + tangent * seconds) * decay;
    double travel = Math.clamp(distance - remaining, 0.0, distance);
    double nextSpeed = Math.max(0.0, (speed + RESPONSE * tangent * seconds) * decay);
    if (travel >= distance) nextSpeed = 0.0;
    List<CarryMotionPath.Node> traversed = new ArrayList<>();
    traversed.add(new CarryMotionPath.Node(current, null));
    for (Vec3 point : route) {
      if (traversed.size() > CarryMotionPath.MAX_POINTS - 4) break;
      Vec3 delta = point.subtract(current);
      double span = delta.length();
      if (span <= 1.0E-12) {
        path.reachWaypoint(point);
        continue;
      }
      double amount = Math.min(travel, span);
      Vec3 requested = current.add(delta.scale(amount / span));
      var swept = collisions.sweep(current, requested);
      Vec3 resolved = swept.path().end();
      velocity = swept.constrain(delta.scale(nextSpeed / span));
      traversed.addAll(swept.path().nodes().subList(1, swept.path().nodes().size()));
      if (swept.path().nodes().stream().anyMatch(n -> n.crossing() != null)) {
        trackingError = null;
        break;
      }
      if (requested.distanceToSqr(resolved) > 1.0E-14) break;
      current = resolved;
      if (amount >= span) path.reachWaypoint(point);
      travel -= amount;
      if (travel <= 1.0E-12) break;
    }
    return CarryMotionPath.ofNodes(traversed);
  }
}
