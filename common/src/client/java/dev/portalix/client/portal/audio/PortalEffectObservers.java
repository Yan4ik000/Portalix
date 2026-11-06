package dev.portalix.client.portal.audio;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.audio.PortalSoundPropagation.Observer;
import dev.portalix.portal.geometry.PortalAperture;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PortalEffectObservers {
  private record Center(BlockPos position, PortalEndpoint exit) {}

  private PortalEffectObservers() {}

  public static Sampling sample(
      ClientLevel level, Vec3 listener, BlockPos primary, int horizontalRadius, int verticalRadius) {
    double horizontal = horizontalRadius + 1.0;
    double vertical = verticalRadius + 1.0;
    double range = Math.sqrt(2 * horizontal * horizontal + vertical * vertical);
    var observers = new ArrayList<Observer>();
    observers.add(new Observer(List.of(), listener, null));
    var centers = new HashSet<Center>();
    for (var observer : PortalAudioRouter.observers(listener, range)) {
      var exit = BlockPos.containing(observer.exit().center());
      var position = BlockPos.containing(observer.position());
      if (!position.equals(primary)
          && level.getChunkSource().hasChunk(exit.getX() >> 4, exit.getZ() >> 4)
          && centers.add(new Center(position, observer.exit())))
        observers.add(observer);
    }
    return new Sampling(observers, primary, horizontalRadius, verticalRadius);
  }

  public static final class Sampling {
    private final List<Observer> observers;
    private final int horizontalRadius;
    private final int verticalRadius;
    private final Node index;
    private final IdentityHashMap<Observer, Region> regions = new IdentityHashMap<>();

    Sampling(List<Observer> observers, BlockPos primaryOrigin, int horizontalRadius, int verticalRadius) {
      this.observers = List.copyOf(observers);
      this.horizontalRadius = horizontalRadius;
      this.verticalRadius = verticalRadius;
      var entries = new Region[observers.size()];
      for (int i = 0; i < observers.size(); i++) {
        var observer = observers.get(i);
        var region = new Region(observer, observer.exit() == null
            ? primaryOrigin : BlockPos.containing(observer.position()), i);
        entries[i] = region;
        regions.put(observer, region);
      }
      index = Node.build(entries, 0, entries.length, horizontalRadius, verticalRadius);
    }

    public List<Observer> observers() {
      return observers;
    }

    public boolean owns(Observer observer, BlockPos sample) {
      return owns(observer, sample, horizontalRadius, verticalRadius);
    }

    public boolean owns(Observer observer, BlockPos sample, int horizontalRadius, int verticalRadius) {
      var region = regions.get(observer);
      if (!region.contains(sample, horizontalRadius, verticalRadius)) return false;
      if (index.region != null)
        return observer.exit() == null || region.inFront(Vec3.atCenterOf(sample));
      var point = Vec3.atCenterOf(sample);
      return region.inFront(point)
          && !index.hasBetter(sample, point, horizontalRadius, verticalRadius, region, region.distance(point));
    }
  }

  private record Region(Observer observer, BlockPos origin, int order) {
    boolean contains(BlockPos sample, int horizontalRadius, int verticalRadius) {
      return Math.abs((long) sample.getX() - origin.getX()) <= horizontalRadius
          && Math.abs((long) sample.getY() - origin.getY()) <= verticalRadius
          && Math.abs((long) sample.getZ() - origin.getZ()) <= horizontalRadius;
    }

    boolean inFront(Vec3 point) {
      return observer.exit() == null || PortalAperture.signedDistance(observer.exit(), point)
          >= -PortalAperture.PLANE_EPSILON;
    }

    AABB samples(int horizontalRadius, int verticalRadius) {
      return new AABB(origin.getX() - (double) horizontalRadius, origin.getY() - (double) verticalRadius,
          origin.getZ() - (double) horizontalRadius, origin.getX() + (double) horizontalRadius + 1,
          origin.getY() + (double) verticalRadius + 1, origin.getZ() + (double) horizontalRadius + 1);
    }

    double distance(Vec3 point) {
      return observer.position().distanceToSqr(point);
    }
  }

  private static final class Node {
    private final AABB samples;
    private final AABB positions;
    private final int firstOrder;
    private final Region region;
    private final Node left;
    private final Node right;

    private Node(AABB samples, AABB positions, int firstOrder, Region region, Node left, Node right) {
      this.samples = samples;
      this.positions = positions;
      this.firstOrder = firstOrder;
      this.region = region;
      this.left = left;
      this.right = right;
    }

    static Node build(Region[] regions, int from, int to, int horizontalRadius, int verticalRadius) {
      var first = regions[from];
      var samples = first.samples(horizontalRadius, verticalRadius);
      var positions = new AABB(first.observer.position(), first.observer.position());
      int firstOrder = first.order;
      for (int i = from + 1; i < to; i++) {
        var region = regions[i];
        samples = samples.minmax(region.samples(horizontalRadius, verticalRadius));
        positions = positions.minmax(new AABB(region.observer.position(), region.observer.position()));
        firstOrder = Math.min(firstOrder, region.order);
      }
      if (to - from == 1) return new Node(samples, positions, firstOrder, first, null, null);
      int axis = positions.getXsize() >= positions.getYsize() ? 0 : 1;
      if (positions.getZsize() > (axis == 0 ? positions.getXsize() : positions.getYsize())) axis = 2;
      int splitAxis = axis;
      Arrays.sort(regions, from, to, Comparator
          .comparingDouble((Region region) -> coordinate(region.observer.position(), splitAxis))
          .thenComparingInt(Region::order));
      int middle = (from + to) >>> 1;
      return new Node(samples, positions, firstOrder, null,
          build(regions, from, middle, horizontalRadius, verticalRadius),
          build(regions, middle, to, horizontalRadius, verticalRadius));
    }

    boolean hasBetter(BlockPos sample, Vec3 point, int horizontalRadius, int verticalRadius,
        Region target, double targetDistance) {
      if (!samples.contains(point)) return false;
      double nearestDistance = positions.distanceToSqr(point);
      if (nearestDistance > targetDistance
          || nearestDistance == targetDistance && firstOrder >= target.order) return false;
      if (region != null)
        return region != target && region.contains(sample, horizontalRadius, verticalRadius)
            && region.inFront(point);
      double leftDistance = left.positions.distanceToSqr(point);
      double rightDistance = right.positions.distanceToSqr(point);
      boolean leftFirst = leftDistance < rightDistance
          || leftDistance == rightDistance && left.firstOrder < right.firstOrder;
      var first = leftFirst ? left : right;
      var second = leftFirst ? right : left;
      return first.hasBetter(sample, point, horizontalRadius, verticalRadius, target, targetDistance)
          || second.hasBetter(sample, point, horizontalRadius, verticalRadius, target, targetDistance);
    }

    private static double coordinate(Vec3 point, int axis) {
      return axis == 0 ? point.x : axis == 1 ? point.y : point.z;
    }
  }
}
