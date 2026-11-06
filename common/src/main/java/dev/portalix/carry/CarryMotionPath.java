package dev.portalix.carry;

import dev.portalix.portal.PortalLink;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CarryMotionPath {
  public static final int MAX_POINTS = 64;
  private final List<Vec3> points;
  private final List<Node> nodes;
  private final double[] distances;
  private final double length;

  public CarryMotionPath(List<Vec3> points) {
    this(points.stream().map(p -> new Node(p, null)).toArray(Node[]::new));
  }

  private CarryMotionPath(Node[] entries) {
    List<Node> nodes = List.of(entries);
    List<Vec3> points = nodes.stream().map(Node::point).toList();
    if (points.isEmpty() || points.size() > MAX_POINTS)
      throw new IllegalArgumentException("Invalid carry path size");
    this.points = List.copyOf(points);
    this.nodes = List.copyOf(nodes);
    if (nodes.getFirst().crossing() != null)
      throw new IllegalArgumentException("Path must start in a world space");
    distances = new double[points.size()];
    for (int i = 1; i < points.size(); i++) {
      PortalLink crossing = nodes.get(i).crossing();
      if (crossing != null
          && crossing.transform().point(points.get(i - 1)).distanceToSqr(points.get(i)) > 1e-10)
        throw new IllegalArgumentException(
            "Carry seam endpoints do not match its portal transform");
      distances[i] =
          distances[i - 1]
              + (nodes.get(i).crossing() == null ? points.get(i - 1).distanceTo(points.get(i)) : 0);
    }
    length = distances[distances.length - 1];
  }

  public static CarryMotionPath ofNodes(List<Node> nodes) {
    return new CarryMotionPath(nodes.toArray(Node[]::new));
  }

  public List<Node> nodes() {
    return nodes;
  }

  public Vec3 vector(Vec3 vector) {
    for (Node node : nodes)
      if (node.crossing() != null) vector = node.crossing().transform().vector(vector);
    return vector;
  }

  public List<PortalLink> crossings(float partial) {
    double distance = length <= 1.0E-12 ? length : Math.clamp(partial, 0.0F, 1.0F) * length;
    var links = new ArrayList<PortalLink>();
    for (int i = 1; i < nodes.size(); i++)
      if (distances[i] <= distance && nodes.get(i).crossing() != null)
        links.add(nodes.get(i).crossing());
    return List.copyOf(links);
  }

  public Vec3 point(Vec3 point) {
    for (Node node : nodes)
      if (node.crossing() != null) point = node.crossing().transform().point(point);
    return point;
  }

  public static CarryMotionPath stationary(Vec3 center) {
    return new CarryMotionPath(List.of(center));
  }

  public Vec3 end() {
    return points.getLast();
  }

  public Vec3 center(float partial) {
    if (length <= 1.0E-12 || partial >= 1) return end();
    double distance = Math.clamp(partial, 0.0F, 1.0F) * length;
    int index = segment(distance);
    double start = distances[index - 1];
    double span = distances[index] - start;
    return points.get(index - 1).lerp(points.get(index), (distance - start) / span);
  }

  public Vec3 movement(float partial) {
    if (length <= 1.0E-12) return Vec3.ZERO;
    int index = segment(Math.clamp(partial, 0.0F, 1.0F) * length);
    Vec3 movement =
        points
            .get(index)
            .subtract(points.get(index - 1))
            .scale(length / (distances[index] - distances[index - 1]));
    if (partial >= 1)
      for (int i = index + 1; i < nodes.size(); i++) {
        if (nodes.get(i).crossing() != null)
          movement = nodes.get(i).crossing().transform().vector(movement);
      }
    return movement;
  }

  private int segment(double distance) {
    for (int i = 1; i < distances.length; i++) {
      if (distance < distances[i] && distances[i] > distances[i - 1]) return i;
    }
    for (int i = distances.length - 1; i > 0; i--) {
      if (distances[i] > distances[i - 1]) return i;
    }
    throw new IllegalStateException("Moving carry path has no segment");
  }

  public AABB bounds(AABB actual) {
    Vec3 center = actual.getCenter();
    AABB result = actual.move(points.getFirst().subtract(center));
    for (int i = 1; i < points.size(); i++) {
      result = result.minmax(actual.move(points.get(i).subtract(center)));
    }
    return result;
  }

  public record Node(Vec3 point, PortalLink crossing) {}
}
