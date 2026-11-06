package dev.portalix.portal.placement;

import static dev.portalix.portal.placement.PortalSurface.EPSILON;

import dev.portalix.api.PortalEndpoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

final class PortalPlacementSolver {
  private static final double SOURCE_X = PortalSurface.WIDTH / 64;
  private static final double SOURCE_Y = PortalSurface.HEIGHT / 112;

  private record Constraint(Vec3 normal, double distance) {
    double missing(Vec3 point) {
      return distance - normal.dot(point);
    }
  }

  private PortalPlacementSolver() {}

  static Optional<PortalEndpoint> findPlacement(
      PortalEndpoint desired, List<PortalEndpoint> occupied, Predicate<BlockPos> surface) {
    var result = findOrientedPlacement(desired, occupied, surface);
    if (result.isPresent() || Math.abs(desired.normal().y) < 1 - EPSILON) return result;

    var axes = new ArrayList<Vec3>(2);
    axes.add(new Vec3(desired.up().x < 0 ? -1 : 1, 0, 0));
    axes.add(new Vec3(0, 0, desired.up().z < 0 ? -1 : 1));
    axes.sort(Comparator.comparingDouble((Vec3 up) -> up.dot(desired.up())).reversed());
    for (Vec3 up : axes) {
      if (up.distanceToSqr(desired.up()) <= EPSILON * EPSILON) continue;
      var aligned =
          new PortalEndpoint(
              desired.dimension(),
              desired.center(),
              desired.normal(),
              up,
              desired.width(),
              desired.height());
      result = findOrientedPlacement(aligned, occupied, surface);
      if (result.isPresent()) return result;
    }
    return Optional.empty();
  }

  private static Optional<PortalEndpoint> findOrientedPlacement(
      PortalEndpoint desired, List<PortalEndpoint> occupied, Predicate<BlockPos> surface) {
    List<PortalRectangle> portals =
        occupied.stream()
            .filter(p -> PortalSurface.coplanar(desired, p))
            .map(PortalRectangle::of)
            .toList();
    PortalRectangle shape = PortalRectangle.of(desired);
    Vec3 center = separate(shape, portals);
    for (int depth = 0; depth <= 5; depth++) {
      if (center.distanceToSqr(desired.center()) > PortalSurface.MAX_BUMP_DISTANCE_SQUARED + EPSILON
          || !supported(center, desired.normal(), surface)) return Optional.empty();
      PortalRectangle rectangle = shape.at(center);
      List<Constraint> constraints = new ArrayList<>(4);
      int blocked = 0;

      for (int y : new int[] {1, -1}) {
        for (int x : new int[] {1, -1}) {
          Vec3 corner =
              center
                  .add(shape.right().scale(x * (shape.halfWidth() - 2 * SOURCE_X)))
                  .add(shape.up().scale(y * (shape.halfHeight() - 2 * SOURCE_Y)));
          var contact = trace(center, corner, desired.normal(), portals, surface);

          if (contact == null)
            contact =
                trace(
                    center,
                    center
                        .add(shape.right().scale(x * shape.halfWidth()))
                        .add(shape.up().scale(y * shape.halfHeight())),
                    desired.normal(),
                    portals,
                    surface);
          if (contact != null) {
            blocked++;
            constraints.add(
                new Constraint(
                    contact.normal(),
                    contact.point().subtract(center).dot(contact.normal())
                        + shape.extent(contact.normal())));
          }
        }
      }
      if (blocked == 0) {
        if (PortalSurface.blocks(rectangle, desired.normal()).stream()
                .anyMatch(p -> !surface.test(p))
            || portals.stream().anyMatch(rectangle::overlaps)) return Optional.empty();
        return Optional.of(
            new PortalEndpoint(
                desired.dimension(),
                center,
                desired.normal(),
                desired.up(),
                desired.width(),
                desired.height()));
      }
      Vec3 shift = project(constraints);
      if (shift == null) {
        if (blocked == 4) return Optional.empty();

        Constraint opposed = null;
        for (int i = 0; i < constraints.size() && opposed == null; i++) {
          for (int j = i + 1; j < constraints.size(); j++) {
            if (constraints.get(i).normal.dot(constraints.get(j).normal) < -.9) {
              opposed = constraints.get(i);
              break;
            }
          }
        }
        if (opposed == null) return Optional.empty();
        Vec3 tangent = opposed.normal.cross(desired.normal()).normalize();
        boolean positive =
            trace(
                    center,
                    center.add(tangent.scale(desired.width())),
                    desired.normal(),
                    portals,
                    surface)
                == null;
        boolean negative =
            trace(
                    center,
                    center.subtract(tangent.scale(desired.width())),
                    desired.normal(),
                    portals,
                    surface)
                == null;
        if (!positive && !negative) return Optional.empty();
        shift = tangent.scale(positive && !negative ? shape.halfWidth() : -shape.halfWidth());
      }
      if (shift.lengthSqr() <= EPSILON * EPSILON) return Optional.empty();
      center = center.add(shift);
    }
    return Optional.empty();
  }

  private static Vec3 separate(PortalRectangle shape, List<PortalRectangle> portals) {
    Vec3 center = shape.center();
    for (PortalRectangle other : portals) {
      Vec3 delta = center.subtract(other.center());
      float dx = (float) (delta.dot(shape.right()) / SOURCE_X);
      float dy = (float) (delta.dot(shape.up()) / SOURCE_Y);
      float distance = (float) Math.sqrt(dx * dx);
      float direction = distance < 1 ? -1 : dx * (1 / (distance + Math.ulp(1F)));
      if (Math.abs(shape.up().dot(other.up())) > 1 - EPSILON) {

        if (distance < 64 && Math.sqrt(dy * dy) < 112) {
          float next = dx + direction * (64 - distance + 1);
          center = center.add(shape.right().scale(next * SOURCE_X - delta.dot(shape.right())));
        }
      } else if (shape.at(center).overlaps(other)) {

        Vec3 movement = shape.right().scale(Math.signum(direction));
        double exit = Double.POSITIVE_INFINITY;
        for (Vec3 axis : shape.axes(other)) {
          double speed = movement.dot(axis);
          if (Math.abs(speed) < EPSILON) continue;
          double reach = shape.extent(axis) + other.extent(axis);
          exit =
              Math.min(exit, (reach - Math.copySign(1, speed) * delta.dot(axis)) / Math.abs(speed));
        }
        center = center.add(movement.scale(exit + SOURCE_X));
      }
    }
    return center;
  }

  private static Vec3 project(List<Constraint> constraints) {
    Vec3 best = null;
    for (int i = 0; i < constraints.size(); i++) {
      Constraint a = constraints.get(i);
      Vec3 candidate = a.normal.scale(a.distance);
      if (satisfies(candidate, constraints)
          && (best == null || candidate.lengthSqr() < best.lengthSqr())) best = candidate;
      for (int j = i + 1; j < constraints.size(); j++) {
        Constraint b = constraints.get(j);
        double cosine = a.normal.dot(b.normal), determinant = 1 - cosine * cosine;
        if (determinant < EPSILON) continue;
        candidate =
            a.normal
                .scale((a.distance - cosine * b.distance) / determinant)
                .add(b.normal.scale((b.distance - cosine * a.distance) / determinant));
        if (satisfies(candidate, constraints)
            && (best == null || candidate.lengthSqr() < best.lengthSqr())) best = candidate;
      }
    }
    return best;
  }

  private static boolean satisfies(Vec3 point, List<Constraint> constraints) {
    return constraints.stream().allMatch(c -> c.missing(point) <= EPSILON);
  }

  private static boolean supported(Vec3 center, Vec3 normal, Predicate<BlockPos> surface) {
    Vec3 backing = center.subtract(normal.scale(.5));

    for (BlockPos block :
        BlockPos.betweenClosed(
            BlockPos.containing(backing.add(-EPSILON, -EPSILON, -EPSILON)),
            BlockPos.containing(backing.add(EPSILON, EPSILON, EPSILON)))) {
      if (surface.test(block)) return true;
    }
    return false;
  }

  private static PortalRectangle.Contact trace(
      Vec3 start,
      Vec3 end,
      Vec3 normal,
      List<PortalRectangle> portals,
      Predicate<BlockPos> surface) {
    PortalRectangle.Contact first = null;
    Vec3 a = start.subtract(normal.scale(.5)), b = end.subtract(normal.scale(.5));
    BlockPos min =
        BlockPos.containing(
            Math.min(a.x, b.x) - EPSILON,
            Math.min(a.y, b.y) - EPSILON,
            Math.min(a.z, b.z) - EPSILON);
    BlockPos max =
        BlockPos.containing(
            Math.max(a.x, b.x) + EPSILON,
            Math.max(a.y, b.y) + EPSILON,
            Math.max(a.z, b.z) + EPSILON);
    for (BlockPos cell : BlockPos.betweenClosed(min, max)) {
      if (surface.test(cell)) continue;
      var hit =
          PortalSurface.blockFace(cell, normal)
              .traceBoundary(
                  start,
                  end,
                  outward -> surface.test(cell.relative(Direction.getApproximateNearest(outward))));
      if (hit != null && (first == null || hit.fraction() < first.fraction())) first = hit;
    }
    for (PortalRectangle portal : portals) {
      var hit = portal.trace(start, end);
      if (hit != null && (first == null || hit.fraction() < first.fraction())) first = hit;
    }
    return first;
  }
}
