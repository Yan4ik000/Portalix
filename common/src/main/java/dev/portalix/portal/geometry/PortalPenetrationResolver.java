package dev.portalix.portal.geometry;

import dev.portalix.resource.SourceUnits;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PortalPenetrationResolver {
  private static final double CORNER_INSET = .009900989942252636;
  private static final double SOURCE_UNIT = SourceUnits.SOURCE_UNIT_BLOCKS;
  private static final double START_OFFSET = .0010000000474974513 * SOURCE_UNIT;

  private PortalPenetrationResolver() {}

  public static Optional<Vec3> displacement(
      List<AABB> hulls, Vec3 direction, Function<AABB, List<AABB>> scene) {
    AABB hull = hulls.stream().reduce(AABB::minmax).orElseThrow();
    Vec3 origin = hull.getCenter();
    Vec3 half = new Vec3(hull.getXsize(), hull.getYsize(), hull.getZsize()).scale(.5);
    Vec3 increment = half.scale(CORNER_INSET);
    Vec3 corner = half.subtract(increment);
    Vec3 center = origin.add(0, START_OFFSET, 0);
    for (int iteration = 0; iteration < 100; iteration++) {
      AABB current = AABB.ofSize(center, half.x * 2, half.y * 2, half.z * 2);
      List<AABB> obstacles = scene.apply(current.minmax(hull));
      Vec3 shift = center.subtract(origin);
      if (hulls.stream()
          .noneMatch(part -> obstacles.stream().anyMatch(part.move(shift)::intersects))) {
        double fraction = 1;
        for (AABB part : hulls)
          for (AABB obstacle : obstacles) {
            Vec3 partStart = part.getCenter().add(shift);
            fraction =
                Math.min(
                    fraction,
                    traceFraction(
                        obstacle.inflate(
                            part.getXsize() / 2, part.getYsize() / 2, part.getZsize() / 2),
                        partStart,
                        part.getCenter()));
          }
        return Optional.of(center.lerp(origin, fraction).subtract(origin));
      }
      Vec3[] points = new Vec3[8];
      boolean[] solid = new boolean[8];
      double[] score = new double[8];
      for (int i = 0; i < 8; i++) {
        points[i] =
            center.add(
                (i & 1) == 0 ? -corner.x : corner.x,
                (i & 2) == 0 ? -corner.y : corner.y,
                (i & 4) == 0 ? -corner.z : corner.z);
        Vec3 point = points[i];
        solid[i] =
            scene.apply(AABB.ofSize(point, 1e-8, 1e-8, 1e-8)).stream()
                .anyMatch(box -> box.contains(point));
      }
      for (int i = 0; i < 8; i++)
        for (int j = i + 1; j < 8; j++) {
          score[i] += solid[i] ? -100 * SOURCE_UNIT : clearDistance(points[i], points[j], scene);
          score[j] += solid[j] ? -100 * SOURCE_UNIT : clearDistance(points[j], points[i], scene);
        }
      Vec3 step = Vec3.ZERO;
      double weight = 0;
      for (int i = 0; i < 8; i++)
        if (score[i] > 0) {
          step = step.add(points[i].subtract(center).scale(score[i]));
          weight += score[i];
        }
      if (weight == 0) {
        step = direction.scale(SOURCE_UNIT);
        corner = half;
      } else {
        step = step.scale(1 / weight);
        corner = corner.subtract(increment);
      }
      center = center.add(step);
    }
    return Optional.empty();
  }

  private static double clearDistance(Vec3 from, Vec3 to, Function<AABB, List<AABB>> scene) {
    double distance = from.distanceTo(to);
    for (AABB obstacle : scene.apply(new AABB(from, to).inflate(1e-8))) {
      distance = Math.min(distance, from.distanceTo(to) * traceFraction(obstacle, from, to));
    }
    return distance;
  }

  private static double traceFraction(AABB box, Vec3 from, Vec3 to) {
    Vec3 delta = to.subtract(from);
    double enter = Double.NEGATIVE_INFINITY, leave = Double.POSITIVE_INFINITY;
    for (var axis : Direction.Axis.values()) {
      double start = from.get(axis), speed = delta.get(axis);
      double min = box.min(axis), max = box.max(axis);
      if (speed == 0) {
        if (start <= min || start >= max) return 1;
      } else {
        double first = (min - start) / speed, last = (max - start) / speed;
        enter = Math.max(enter, Math.min(first, last));
        leave = Math.min(leave, Math.max(first, last));
        if (enter > leave) return 1;
      }
    }
    return leave <= 0 || enter > 1 ? 1 : Math.max(0, enter);
  }
}
