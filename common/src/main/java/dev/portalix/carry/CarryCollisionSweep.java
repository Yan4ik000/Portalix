package dev.portalix.carry;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CarryCollisionSweep {
  private static final double CONTACT_GAP = 1.0E-7;

  private CarryCollisionSweep() {}

  public record Result(CarryMotionPath path, List<Vec3> contactNormals) {
    public Result {
      contactNormals = List.copyOf(contactNormals);
    }

    public Result(CarryMotionPath path, int blockedAxes) {
      this(path, axes(blockedAxes));
    }

    public Vec3 constrain(Vec3 velocity) {
      velocity = path.vector(velocity);
      if (contactNormals.isEmpty()) return velocity;
      var basis = new ArrayList<Vec3>();
      for (Vec3 normal : contactNormals) {
        for (Vec3 prior : basis) normal = normal.subtract(prior.scale(normal.dot(prior)));
        if (normal.lengthSqr() < 1e-12) continue;
        normal = normal.normalize();
        basis.add(normal);
        velocity = velocity.subtract(normal.scale(velocity.dot(normal)));
      }
      return velocity;
    }
  }

  static List<Vec3> axes(int mask) {
    var result = new ArrayList<Vec3>();
    if ((mask & 1) != 0) result.add(new Vec3(1, 0, 0));
    if ((mask & 2) != 0) result.add(new Vec3(0, 1, 0));
    if ((mask & 4) != 0) result.add(new Vec3(0, 0, 1));
    return result;
  }

  public static Result move(AABB hull, Vec3 displacement, List<AABB> obstacles) {
    Vec3 center = hull.getCenter();
    if (displacement.lengthSqr() <= 1e-20)
      return new Result(CarryMotionPath.stationary(center), List.of());
    if (obstacles.isEmpty())
      return new Result(new CarryMotionPath(List.of(center, center.add(displacement))), List.of());
    List<AABB> expanded =
        obstacles.stream()
            .map(
                box ->
                    box.inflate(hull.getXsize() * .5, hull.getYsize() * .5, hull.getZsize() * .5))
            .toList();
    var points = new ArrayList<Vec3>();
    points.add(center);
    int blocked = 0;
    Vec3 remaining = displacement;

    while (remaining.lengthSqr() > 1.0E-20) {
      double first = 1;
      int axes = 0;
      for (AABB box : expanded) {
        Hit hit = intersect(center, remaining, box);
        if (hit == null) continue;
        if (hit.time < first - 1.0E-10) {
          first = hit.time;
          axes = hit.axes;
        } else if (Math.abs(hit.time - first) <= 1.0E-10) axes |= hit.axes;
      }
      if (axes == 0) {
        points.add(center.add(remaining));
        break;
      }
      double travel = Math.max(0, first - CONTACT_GAP / remaining.length());
      Vec3 contact = center.add(remaining.scale(travel));
      if (!contact.equals(center)) points.add(contact);
      center = contact;
      blocked |= axes;
      remaining =
          new Vec3(
              (axes & 1) == 0 ? remaining.x * (1 - travel) : 0,
              (axes & 2) == 0 ? remaining.y * (1 - travel) : 0,
              (axes & 4) == 0 ? remaining.z * (1 - travel) : 0);
    }
    return new Result(new CarryMotionPath(points), blocked);
  }

  private static Hit intersect(Vec3 start, Vec3 delta, AABB box) {
    int penetration = penetrationAxes(box, start, delta);
    if (penetration >= 0) return penetration == 0 ? null : new Hit(0, penetration);
    double enter = Double.NEGATIVE_INFINITY;
    double leave = Double.POSITIVE_INFINITY;
    int axes = 0;
    for (int axis = 0; axis < 3; axis++) {
      double from = axis == 0 ? start.x : axis == 1 ? start.y : start.z;
      double movement = axis == 0 ? delta.x : axis == 1 ? delta.y : delta.z;
      double min = axis == 0 ? box.minX : axis == 1 ? box.minY : box.minZ;
      double max = axis == 0 ? box.maxX : axis == 1 ? box.maxY : box.maxZ;
      if (Math.abs(movement) < 1.0E-12) {
        if (from <= min || from >= max) return null;
        continue;
      }
      double a = (min - from) / movement;
      double b = (max - from) / movement;
      double near = Math.min(a, b);
      if (near > enter + 1.0E-10) {
        enter = near;
        axes = 1 << axis;
      } else if (Math.abs(near - enter) <= 1.0E-10) axes |= 1 << axis;
      leave = Math.min(leave, Math.max(a, b));
      if (enter >= leave) return null;
    }
    return enter >= 0 && enter <= 1 && leave > 0 ? new Hit(enter, axes) : null;
  }

  static int penetrationAxes(AABB box, Vec3 start, Vec3 delta) {
    if (start.x <= box.minX
        || start.x >= box.maxX
        || start.y <= box.minY
        || start.y >= box.maxY
        || start.z <= box.minZ
        || start.z >= box.maxZ) return -1;
    double depth =
        Math.min(
                Math.min(start.x - box.minX, box.maxX - start.x),
                Math.min(
                    Math.min(start.y - box.minY, box.maxY - start.y),
                    Math.min(start.z - box.minZ, box.maxZ - start.z)))
            + CONTACT_GAP;
    boolean escaping =
        start.x - box.minX <= depth && delta.x < 0
            || box.maxX - start.x <= depth && delta.x > 0
            || start.y - box.minY <= depth && delta.y < 0
            || box.maxY - start.y <= depth && delta.y > 0
            || start.z - box.minZ <= depth && delta.z < 0
            || box.maxZ - start.z <= depth && delta.z > 0;
    return escaping ? 0 : 7;
  }

  private record Hit(double time, int axes) {}
}
