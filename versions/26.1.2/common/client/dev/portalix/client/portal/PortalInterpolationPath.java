package dev.portalix.client.portal;

import dev.portalix.portal.camera.PortalLookAngles;
import dev.portalix.portal.traversal.PortalEntityMovement.Crossing;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public final class PortalInterpolationPath {
  private final List<Crossing> crossings = new ArrayList<>();

  public void append(List<Crossing> route) {
    crossings.addAll(route);
  }

  public boolean isEmpty() {
    return crossings.isEmpty();
  }

  public Crossing first() {
    return crossings.getFirst();
  }

  public List<Crossing> crossings() {
    return List.copyOf(crossings);
  }

  public void removeFirst() {
    crossings.removeFirst();
  }

  public void clear() {
    crossings.clear();
  }

  public PortalLookAngles sourceAngles(float yaw, float pitch) {
    var angles = new PortalLookAngles(yaw, pitch);
    for (var crossing : crossings.reversed())
      angles =
          PortalLookAngles.transform(
              crossing.link().transform().inverse(), angles.yaw(), angles.pitch());
    return angles;
  }

  public Vec3 sourceVector(Vec3 vector) {
    for (var crossing : crossings.reversed())
      vector = crossing.link().transform().inverse().vector(vector);
    return vector;
  }

  public Sample advance(Vec3 position, Vec3 target, double alpha) {
    double length = 0;
    Vec3 cursor = position;
    for (var crossing : crossings) {
      length += cursor.distanceTo(crossing.beforeFeet());
      cursor = crossing.afterFeet();
    }
    length += cursor.distanceTo(target);
    double distance = length * alpha;
    double remaining = distance;
    var passed = new ArrayList<TimedCrossing>();
    while (!crossings.isEmpty()) {
      var crossing = crossings.getFirst();
      double approach = position.distanceTo(crossing.beforeFeet());
      if (remaining < approach && alpha < 1) break;
      remaining = Math.max(0, remaining - approach);
      crossings.removeFirst();
      passed.add(
          new TimedCrossing(crossing, distance == 0 ? 0 : (distance - remaining) / distance));
      position = crossing.afterFeet();
    }
    Vec3 end = crossings.isEmpty() ? target : crossings.getFirst().beforeFeet();
    double segment = position.distanceTo(end);
    return new Sample(
        segment == 0 ? end : position.lerp(end, Math.min(1, remaining / segment)),
        List.copyOf(passed));
  }

  public record TimedCrossing(Crossing crossing, double fraction) {}

  public record Sample(Vec3 position, List<TimedCrossing> crossings) {}
}
