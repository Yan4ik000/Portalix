package dev.portalix.portal.geometry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ConvexPolyhedron {
  private static final double EPSILON = 1.0E-9;
  private static final List<Vec3> BOX_EDGES =
      List.of(new Vec3(0, 0, 1), new Vec3(0, -1, 0), new Vec3(1, 0, 0));
  private final List<Plane> planes;
  private final List<Vec3> vertices;
  private final Vec3 origin;
  private volatile List<Vec3> edgeDirections;
  private volatile int emptiness;

  private ConvexPolyhedron(List<Plane> planes, Vec3 origin) {
    this.planes = List.copyOf(planes);
    this.origin = origin;
    var points = new ArrayList<Vec3>();
    for (int i = 0; i < planes.size(); i++) {
      Plane a = planes.get(i);
      for (int j = i + 1; j < planes.size(); j++) {
        Plane b = planes.get(j);
        for (int k = j + 1; k < planes.size(); k++) {
          Plane c = planes.get(k);
          Vec3 cross = b.normal.cross(c.normal);
          double determinant = a.normal.dot(cross);
          if (Math.abs(determinant) < EPSILON) continue;
          Vec3 point =
              cross
                  .scale(a.offset - a.normal.dot(origin))
                  .add(c.normal.cross(a.normal).scale(b.offset - b.normal.dot(origin)))
                  .add(a.normal.cross(b.normal).scale(c.offset - c.normal.dot(origin)))
                  .scale(1 / determinant)
                  .add(origin);
          if (inside(planes, point) && !containsVertex(points, point)) {
            points.add(point);
          }
        }
      }
    }
    vertices = List.copyOf(points);
  }

  private ConvexPolyhedron(List<Plane> planes, Vec3 origin, List<Vec3> vertices) {
    this.planes = List.copyOf(planes);
    this.origin = origin;
    this.vertices = List.copyOf(vertices);
  }

  public static ConvexPolyhedron box(AABB box) {
    var planes =
        List.of(
            new Plane(new Vec3(1, 0, 0), box.maxX), new Plane(new Vec3(-1, 0, 0), -box.minX),
            new Plane(new Vec3(0, 1, 0), box.maxY), new Plane(new Vec3(0, -1, 0), -box.minY),
            new Plane(new Vec3(0, 0, 1), box.maxZ), new Plane(new Vec3(0, 0, -1), -box.minZ));
    if (box.getXsize() <= EPSILON || box.getYsize() <= EPSILON || box.getZsize() <= EPSILON)
      return new ConvexPolyhedron(planes, box.getCenter());
    var hull =
        new ConvexPolyhedron(
            planes,
            box.getCenter(),
            List.of(
                new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.minX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.minZ), new Vec3(box.minX, box.maxY, box.maxZ),
                new Vec3(box.maxX, box.minY, box.minZ), new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.maxX, box.maxY, box.minZ), new Vec3(box.maxX, box.maxY, box.maxZ)));
    hull.edgeDirections = BOX_EDGES;
    return hull;
  }

  public List<Vec3> vertices() {
    return vertices;
  }

  public boolean isEmpty() {
    if (emptiness == 0) emptiness = computeEmpty() ? 1 : 2;
    return emptiness == 1;
  }

  private boolean computeEmpty() {
    if (vertices.size() < 4) return true;
    Vec3 origin = vertices.getFirst();
    for (int i = 1; i < vertices.size(); i++) {
      Vec3 a = vertices.get(i).subtract(origin);
      for (int j = i + 1; j < vertices.size(); j++) {
        Vec3 cross = a.cross(vertices.get(j).subtract(origin));
        for (int k = j + 1; k < vertices.size(); k++) {
          if (Math.abs(cross.dot(vertices.get(k).subtract(origin))) > EPSILON * EPSILON)
            return false;
        }
      }
    }
    return true;
  }

  public boolean contains(Vec3 point) {
    return inside(planes, point);
  }

  public Optional<Vec3> closestPoint(Vec3 point) {
    if (vertices.isEmpty()) return Optional.empty();
    if (contains(point)) return Optional.of(point);
    Vec3 closest = vertices.getFirst();
    for (Plane plane : planes) {
      Vec3 projected =
          point.subtract(
              plane.normal.scale(
                  (plane.normal.dot(point) - plane.offset) / plane.normal.lengthSqr()));
      if (contains(projected) && projected.distanceToSqr(point) < closest.distanceToSqr(point))
        closest = projected;
    }
    for (int i = 0; i < vertices.size(); i++) {
      Vec3 start = vertices.get(i);
      for (int j = i + 1; j < vertices.size(); j++) {
        Vec3 edge = vertices.get(j).subtract(start);
        Vec3 projected =
            start.add(
                edge.scale(Math.clamp(point.subtract(start).dot(edge) / edge.lengthSqr(), 0, 1)));
        if (projected.distanceToSqr(point) < closest.distanceToSqr(point)) closest = projected;
      }
    }
    return Optional.of(closest);
  }

  public ConvexPolyhedron clip(Plane plane) {
    if (vertices.isEmpty()) return this;
    double minimum = Double.POSITIVE_INFINITY, maximum = Double.NEGATIVE_INFINITY;
    for (Vec3 vertex : vertices) {
      double distance = plane.normal.dot(vertex) - plane.offset;
      minimum = Math.min(minimum, distance);
      maximum = Math.max(maximum, distance);
    }
    if (maximum <= 0) return this;
    var clipped = new ArrayList<>(planes);
    clipped.add(plane);
    if (minimum > tolerance(plane)) return new ConvexPolyhedron(clipped, origin, List.of());
    return new ConvexPolyhedron(clipped, origin);
  }

  public ConvexPolyhedron transform(PortalTransform transform) {
    Vec3 transformedOrigin = transform.point(origin);
    return new ConvexPolyhedron(
        planes.stream()
            .map(
                p -> {
                  Vec3 normal = transform.vector(p.normal);
                  return new Plane(
                      normal,
                      normal.dot(transformedOrigin) + (p.offset - p.normal.dot(origin)));
                })
            .toList(),
        transformedOrigin,
        vertices.stream().map(transform::point).toList());
  }

  public AABB bounds() {
    if (vertices.isEmpty()) throw new IllegalStateException("Empty polyhedron has no bounds");
    Vec3 first = vertices.getFirst();
    double minX = first.x,
        minY = first.y,
        minZ = first.z,
        maxX = first.x,
        maxY = first.y,
        maxZ = first.z;
    for (Vec3 p : vertices) {
      minX = Math.min(minX, p.x);
      minY = Math.min(minY, p.y);
      minZ = Math.min(minZ, p.z);
      maxX = Math.max(maxX, p.x);
      maxY = Math.max(maxY, p.y);
      maxZ = Math.max(maxZ, p.z);
    }
    return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
  }

  public Optional<Hit> sweep(Vec3 displacement, ConvexPolyhedron obstacle) {
    if (isEmpty() || obstacle.isEmpty()) return Optional.empty();
    var axes = planeAxes(obstacle);
    boolean edgesAdded = false;
    double enter = Double.NEGATIVE_INFINITY;
    double leave = Double.POSITIVE_INFINITY;
    Vec3 hitNormal = Vec3.ZERO;
    boolean overlapping = true;
    double penetration = Double.POSITIVE_INFINITY;
    Vec3 escapeNormal = Vec3.ZERO;
    for (int index = 0; ; index++) {
      if (index == axes.size()) {
        if (edgesAdded) break;
        addEdgeAxes(axes, obstacle);
        edgesAdded = true;
        if (index == axes.size()) break;
      }
      Vec3 axis = axes.get(index);
      double aMin = min(axis),
          aMax = max(axis),
          bMin = obstacle.min(axis),
          bMax = obstacle.max(axis);
      if (aMax <= bMin + EPSILON || bMax <= aMin + EPSILON) overlapping = false;
      if (aMax - bMin < penetration) {
        penetration = aMax - bMin;
        escapeNormal = axis.scale(-1);
      }
      if (bMax - aMin < penetration) {
        penetration = bMax - aMin;
        escapeNormal = axis;
      }
      double speed = displacement.dot(axis);
      if (Math.abs(speed) < EPSILON) {
        if (aMax <= bMin + EPSILON || bMax <= aMin + EPSILON) return Optional.empty();
        continue;
      }
      double first = (bMin - aMax) / speed;
      double last = (bMax - aMin) / speed;
      Vec3 normal = speed > 0 ? axis.scale(-1) : axis;
      if (first > last) {
        double swap = first;
        first = last;
        last = swap;
      }
      if (first > enter) {
        enter = first;
        hitNormal = normal;
      }
      leave = Math.min(leave, last);
      if (enter > leave || enter > 1) return Optional.empty();
    }
    if (leave <= EPSILON || enter > 1 || (!overlapping && enter < -EPSILON))
      return Optional.empty();
    return Optional.of(
        new Hit(Math.max(0, enter), overlapping ? escapeNormal : hitNormal, overlapping));
  }

  public boolean intersects(ConvexPolyhedron other) {
    if (isEmpty() || other.isEmpty()) return false;
    var axes = planeAxes(other);
    boolean edgesAdded = false;
    for (int index = 0; ; index++) {
      if (index == axes.size()) {
        if (edgesAdded) break;
        addEdgeAxes(axes, other);
        edgesAdded = true;
        if (index == axes.size()) break;
      }
      Vec3 axis = axes.get(index);
      if (max(axis) <= other.min(axis) + EPSILON || other.max(axis) <= min(axis) + EPSILON)
        return false;
    }
    return true;
  }

  private List<Vec3> planeAxes(ConvexPolyhedron other) {
    var axes = new ArrayList<Vec3>();
    for (Plane plane : planes) addAxis(axes, plane.normal.x, plane.normal.y, plane.normal.z);
    for (Plane plane : other.planes) addAxis(axes, plane.normal.x, plane.normal.y, plane.normal.z);
    return axes;
  }

  private void addEdgeAxes(List<Vec3> axes, ConvexPolyhedron other) {
    var otherEdges = other.edges();
    for (Vec3 a : edges())
      for (Vec3 b : otherEdges)
        addAxis(axes, a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
  }

  private static void addAxis(List<Vec3> axes, double x, double y, double z) {
    double lengthSquared = x * x + y * y + z * z;
    if (lengthSquared < EPSILON * EPSILON) return;
    double length = Math.sqrt(lengthSquared);
    boolean zero = length < 1.0E-5F;
    if (zero) {
      x = y = z = 0;
    } else {
      x /= length;
      y /= length;
      z /= length;
    }
    if (!hasParallelAxis(axes, x, y, z)) axes.add(zero ? Vec3.ZERO : new Vec3(x, y, z));
  }

  private List<Vec3> edges() {
    var existing = edgeDirections;
    if (existing != null) return existing;
    var directions = new ArrayList<Vec3>();
    for (int i = 0; i < planes.size(); i++)
      for (int j = i + 1; j < planes.size(); j++) {
        Plane a = planes.get(i), b = planes.get(j);
        double x = a.normal.y * b.normal.z - a.normal.z * b.normal.y;
        double y = a.normal.z * b.normal.x - a.normal.x * b.normal.z;
        double z = a.normal.x * b.normal.y - a.normal.y * b.normal.x;
        double lengthSquared = x * x + y * y + z * z;
        if (lengthSquared < EPSILON * EPSILON) continue;
        double length = Math.sqrt(lengthSquared);
        boolean zero = length < 1.0E-5F;
        if (zero) {
          x = y = z = 0;
        } else {
          x /= length;
          y /= length;
          z /= length;
        }
        if (hasParallelAxis(directions, x, y, z)) continue;
        int count = 0;
        for (Vec3 p : vertices)
          if (Math.abs(a.normal.dot(p) - a.offset) <= tolerance(a)
              && Math.abs(b.normal.dot(p) - b.offset) <= tolerance(b)
              && ++count == 2) break;
        if (count >= 2) directions.add(zero ? Vec3.ZERO : new Vec3(x, y, z));
      }
    existing = List.copyOf(directions);
    edgeDirections = existing;
    return existing;
  }

  private static boolean hasParallelAxis(
      List<Vec3> axes, double axisX, double axisY, double axisZ) {
    for (Vec3 a : axes) {
      double x = a.y * axisZ - a.z * axisY;
      double y = a.z * axisX - a.x * axisZ;
      double z = a.x * axisY - a.y * axisX;
      if (x * x + y * y + z * z < EPSILON * EPSILON) return true;
    }
    return false;
  }

  private static boolean containsVertex(List<Vec3> vertices, Vec3 point) {
    for (Vec3 vertex : vertices) if (vertex.distanceToSqr(point) < EPSILON * EPSILON) return true;
    return false;
  }

  private double min(Vec3 axis) {
    double result = Double.POSITIVE_INFINITY;
    for (Vec3 p : vertices) result = Math.min(result, p.dot(axis));
    return result;
  }

  private double max(Vec3 axis) {
    double result = Double.NEGATIVE_INFINITY;
    for (Vec3 p : vertices) result = Math.max(result, p.dot(axis));
    return result;
  }

  private static boolean inside(List<Plane> planes, Vec3 point) {
    for (Plane plane : planes)
      if (plane.normal.dot(point) > plane.offset + tolerance(plane)) return false;
    return true;
  }

  private static double tolerance(Plane plane) {
    return EPSILON + 8 * Math.ulp(plane.offset);
  }

  public record Plane(Vec3 normal, double offset) {
    public Plane {
      double length = normal.length();
      if (!Double.isFinite(length) || length == 0 || !Double.isFinite(offset))
        throw new IllegalArgumentException("Invalid plane");
      normal = normal.scale(1 / length);
      offset /= length;
    }

    public Plane opposite() {
      return new Plane(normal.scale(-1), -offset);
    }

    public static Plane through(Vec3 normal, Vec3 point) {
      return new Plane(normal, normal.dot(point));
    }
  }

  public record Hit(double fraction, Vec3 normal, boolean startedInside) {}
}
