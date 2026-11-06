package dev.portalix.carry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class CarryPathfinder {
  private static final Comparator<Vec3> POINT_ORDER =
      Comparator.comparingDouble((Vec3 p) -> p.x)
          .thenComparingDouble(p -> p.y)
          .thenComparingDouble(p -> p.z);
  private static final Comparator<AABB> BOX_ORDER =
      Comparator.comparingDouble((AABB b) -> b.minX)
          .thenComparingDouble(b -> b.minY)
          .thenComparingDouble(b -> b.minZ)
          .thenComparingDouble(b -> b.maxX)
          .thenComparingDouble(b -> b.maxY)
          .thenComparingDouble(b -> b.maxZ);
  private static final int MAX_OBSTACLES = 128;
  private static final int MAX_SHAPES = 256;
  private static final int MAX_NODES = 256;
  private static final int MAX_CANDIDATES = 2048;
  private static final int MAX_WORK = 1024;
  private static final double MAX_QUERY_VOLUME = 4096;
  private final List<Vec3> route = new ArrayList<>();
  private AABB region;
  private Planner planner;
  private boolean followingRoute;
  private Vec3 activeGoal;

  boolean hasDetour() {
    return followingRoute;
  }

  List<Vec3> remainingWaypoints() {
    List<Vec3> points = new ArrayList<>(route);
    points.add(activeGoal);
    return points;
  }

  void reachWaypoint(Vec3 point) {
    if (!route.isEmpty() && route.getFirst().equals(point)) route.removeFirst();
  }

  Vec3 steeringTarget(CarryCollisionWorld collisions, Vec3 current, Vec3 desired) {
    followingRoute = false;
    activeGoal = desired;
    if (collisions.crossesPortal(current, desired)) {
      planner = null;
      route.clear();
      region = null;
      return desired;
    }
    AABB bounds = collisions.bounds();
    double reach =
        Math.max(
            CarryAnchor.DISTANCE,
            Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize())));
    AABB interior = region == null ? null : region.deflate(reach * .5);
    if (interior == null || !interior.contains(current) || !interior.contains(desired)) {
      region = new AABB(current, desired).inflate(reach);
      planner = null;
      route.clear();
    }
    List<AABB> obstacles = obstacles(collisions, region);
    if (obstacles == null) {
      planner = null;
      route.clear();
      return desired;
    }
    Vec3 goal = nearestFreeGoal(desired, obstacles);
    if (goal == null) {
      planner = null;
      route.clear();
      return desired;
    }
    activeGoal = goal;
    if (hasClearPath(current, goal, obstacles)) {
      route.clear();
      planner = null;
      return goal;
    }
    if (!route.isEmpty()) {

      if (hasClearPath(current, route.getFirst(), obstacles)
          && hasClearPath(route.getLast(), goal, obstacles)) {
        followingRoute = true;
        return route.getFirst();
      }
      route.clear();
      planner = null;
    }
    if (planner == null
        || !planner.snapshot.equals(obstacles)
        || planner.status != Status.SEARCHING
            && (!planner.start.equals(current) || !planner.goal.equals(goal))) {
      planner = new Planner(current, goal, obstacles, region);
    }
    planner.advance();
    if (planner.status == Status.FOUND && !planner.detours.isEmpty()) {
      if (hasClearPath(current, planner.detours.getFirst(), obstacles)
          && hasClearPath(planner.detours.getLast(), goal, obstacles)) {
        route.addAll(planner.detours);
        followingRoute = true;
        planner = null;
        return route.getFirst();
      }
      planner = null;
    }
    return goal;
  }

  private static List<AABB> obstacles(CarryCollisionWorld collisions, AABB region) {
    AABB bounds = collisions.bounds();
    double x = bounds.getXsize() * .5, y = bounds.getYsize() * .5, z = bounds.getZsize() * .5;
    AABB query =
        region.inflate(
            x + CarryAnchor.SWEEP_MARGIN,
            y + CarryAnchor.SWEEP_MARGIN,
            z + CarryAnchor.SWEEP_MARGIN);
    if (query.getXsize() * query.getYsize() * query.getZsize() > MAX_QUERY_VOLUME) return null;
    List<AABB> boxes = collisions.obstacles(query, MAX_SHAPES, MAX_OBSTACLES);
    if (boxes == null) return null;
    List<AABB> result = new ArrayList<>(boxes.size());
    for (AABB box : boxes) result.add(box.inflate(x, y, z));
    result.sort(BOX_ORDER);
    return result;
  }

  private static boolean hasClearPath(Vec3 start, Vec3 end, List<AABB> obstacles) {
    for (AABB box : obstacles) if (blocks(box, start, end)) return false;
    return true;
  }

  private static boolean blocks(AABB box, Vec3 start, Vec3 end) {
    int penetration = CarryCollisionSweep.penetrationAxes(box, start, end.subtract(start));
    if (penetration >= 0) return penetration != 0;
    double enter = 0.0;
    double leave = 1.0;
    for (int axis = 0; axis < 3; axis++) {
      double from = axis == 0 ? start.x : axis == 1 ? start.y : start.z;
      double to = axis == 0 ? end.x : axis == 1 ? end.y : end.z;
      double min = axis == 0 ? box.minX : axis == 1 ? box.minY : box.minZ;
      double max = axis == 0 ? box.maxX : axis == 1 ? box.maxY : box.maxZ;
      double delta = to - from;
      if (Math.abs(delta) < 1.0E-12) {

        if (from <= min || from >= max) return false;
      } else {
        double first = (min - from) / delta;
        double last = (max - from) / delta;
        enter = Math.max(enter, Math.min(first, last));
        leave = Math.min(leave, Math.max(first, last));
        if (enter >= leave) return false;
      }
    }
    return enter < leave;
  }

  private static boolean inside(AABB box, Vec3 point) {
    return point.x > box.minX
        && point.x < box.maxX
        && point.y > box.minY
        && point.y < box.maxY
        && point.z > box.minZ
        && point.z < box.maxZ;
  }

  private static boolean free(Vec3 point, List<AABB> obstacles) {
    for (AABB box : obstacles) if (inside(box, point)) return false;
    return true;
  }

  private static Vec3 corner(AABB box, Vec3 reference, int index) {
    int x = index / 9, y = index / 3 % 3, z = index % 3;
    double margin = CarryAnchor.SWEEP_MARGIN;
    return new Vec3(
        x == 0
            ? box.minX - margin
            : x == 2 ? box.maxX + margin : Math.clamp(reference.x, box.minX, box.maxX),
        y == 0
            ? box.minY - margin
            : y == 2 ? box.maxY + margin : Math.clamp(reference.y, box.minY, box.maxY),
        z == 0
            ? box.minZ - margin
            : z == 2 ? box.maxZ + margin : Math.clamp(reference.z, box.minZ, box.maxZ));
  }

  private static Vec3 nearestFreeGoal(Vec3 desired, List<AABB> obstacles) {
    if (free(desired, obstacles)) return desired;
    Set<Vec3> candidates = new HashSet<>();
    for (AABB box : obstacles) {
      if (!inside(box, desired)) continue;
      for (int i = 0; i < 27; i++) {
        if (i != 13) candidates.add(corner(box, desired, i));
        if (candidates.size() > MAX_CANDIDATES) return null;
      }
    }

    return candidates.stream()
        .sorted(
            Comparator.comparingDouble((Vec3 p) -> desired.distanceToSqr(p))
                .thenComparing(POINT_ORDER))
        .filter(point -> free(point, obstacles))
        .findFirst()
        .orElse(null);
  }

  private enum Status {
    SEARCHING,
    FOUND,
    NO_PATH,
    CAPACITY_EXCEEDED
  }

  private enum Phase {
    MERGE,
    CANDIDATES,
    FILTER,
    SEARCH
  }

  private static final class Planner {
    final Vec3 start, goal;
    final AABB region;
    final List<AABB> snapshot, boxes;
    final Set<Vec3> candidates = new HashSet<>();
    final List<Vec3> nodes = new ArrayList<>(), detours = new ArrayList<>();
    final PriorityQueue<SearchEntry> pending =
        new PriorityQueue<>(
            Comparator.comparingDouble(SearchEntry::estimate).thenComparingInt(SearchEntry::index));
    Status status = Status.SEARCHING;
    Phase phase = Phase.MERGE;
    int remainingWork;
    int mergeIndex;
    int mergeCandidate = 1;
    int generatedCandidates;
    int filteredCandidates;
    int currentNode = -1;
    int nextNeighbor;
    boolean mergedBoxes;
    List<Vec3> ordered;
    double[] costs;
    int[] parents;
    boolean[] closed;

    Planner(Vec3 start, Vec3 goal, List<AABB> obstacles, AABB region) {
      this.start = start;
      this.goal = goal;
      this.region = region;
      snapshot = List.copyOf(obstacles);
      boxes = new ArrayList<>(obstacles);
      nodes.add(start);
      nodes.add(goal);
    }

    void advance() {
      remainingWork = MAX_WORK;
      while (status == Status.SEARCHING) {
        boolean advanced =
            switch (phase) {
              case MERGE -> mergeObstacles();
              case CANDIDATES -> generateCandidates();
              case FILTER -> filterCandidates();
              case SEARCH -> searchNextNode();
            };
        if (!advanced) return;
      }
    }

    private boolean mergeObstacles() {
      if (mergeIndex >= boxes.size()) {
        if (mergedBoxes) {
          mergeIndex = 0;
          mergeCandidate = 1;
          mergedBoxes = false;
        } else phase = Phase.CANDIDATES;
        return true;
      }
      if (mergeCandidate >= boxes.size()) {
        mergeCandidate = ++mergeIndex + 1;
        return true;
      }
      if (!spend(1)) return false;
      AABB first = boxes.get(mergeIndex);
      AABB second = boxes.get(mergeCandidate);
      if (mergeable(first, second)) {
        boxes.set(mergeIndex, first.minmax(second));
        boxes.remove(mergeCandidate);
        mergedBoxes = true;
      } else mergeCandidate++;
      return true;
    }

    private boolean generateCandidates() {
      if (generatedCandidates >= boxes.size() * 54) {
        ordered =
            candidates.stream()
                .sorted(
                    Comparator.comparingDouble(
                            (Vec3 point) -> start.distanceTo(point) + point.distanceTo(goal))
                        .thenComparing(POINT_ORDER))
                .toList();
        phase = Phase.FILTER;
        return true;
      }
      if (!spend(1)) return false;
      int index = generatedCandidates++;
      if (index % 27 != 13) {
        candidates.add(corner(boxes.get(index / 54), index % 54 < 27 ? goal : start, index % 27));
      }
      if (candidates.size() > MAX_CANDIDATES) status = Status.CAPACITY_EXCEEDED;
      return true;
    }

    private boolean filterCandidates() {
      if (filteredCandidates >= ordered.size() || nodes.size() >= MAX_NODES) {
        costs = new double[nodes.size()];
        Arrays.fill(costs, Double.POSITIVE_INFINITY);
        parents = new int[nodes.size()];
        Arrays.fill(parents, -1);
        closed = new boolean[nodes.size()];
        costs[0] = 0;
        pending.add(new SearchEntry(0, start.distanceTo(goal)));
        phase = Phase.SEARCH;
        return true;
      }
      if (!spend(boxes.size() + 1)) return false;
      Vec3 point = ordered.get(filteredCandidates++);
      if (region.contains(point) && free(point, boxes) && !point.equals(start) && !point.equals(goal)) {
        nodes.add(point);
      }
      return true;
    }

    private boolean searchNextNode() {
      if (currentNode < 0) {
        if (pending.isEmpty()) {
          status = Status.NO_PATH;
          return true;
        }
        if (!spend(1)) return false;
        int index = pending.remove().index();
        if (closed[index]) return true;
        if (index == 1) {
          for (int parent = parents[1]; parent > 0; parent = parents[parent]) {
            detours.addFirst(nodes.get(parent));
          }
          status = Status.FOUND;
          return true;
        }
        currentNode = index;
        closed[currentNode] = true;
        nextNeighbor = 1;
      }
      if (nextNeighbor >= nodes.size()) {
        currentNode = -1;
        return true;
      }
      if (!spend(boxes.size() + 1)) return false;
      int destination = nextNeighbor++;
      if (closed[destination]) return true;
      double cost = costs[currentNode] + nodes.get(currentNode).distanceTo(nodes.get(destination));
      if (cost >= costs[destination]
          || !hasClearPath(nodes.get(currentNode), nodes.get(destination), boxes)) return true;
      costs[destination] = cost;
      parents[destination] = currentNode;
      pending.add(new SearchEntry(destination, cost + nodes.get(destination).distanceTo(goal)));
      return true;
    }

    private boolean spend(int amount) {
      if (amount > remainingWork) return false;
      remainingWork -= amount;
      return true;
    }
  }

  private static boolean mergeable(AABB a, AABB b) {
    boolean x =
        Math.abs(a.minY - b.minY) < 1.0E-6
            && Math.abs(a.maxY - b.maxY) < 1.0E-6
            && Math.abs(a.minZ - b.minZ) < 1.0E-6
            && Math.abs(a.maxZ - b.maxZ) < 1.0E-6
            && a.minX <= b.maxX
            && b.minX <= a.maxX;
    boolean y =
        Math.abs(a.minX - b.minX) < 1.0E-6
            && Math.abs(a.maxX - b.maxX) < 1.0E-6
            && Math.abs(a.minZ - b.minZ) < 1.0E-6
            && Math.abs(a.maxZ - b.maxZ) < 1.0E-6
            && a.minY <= b.maxY
            && b.minY <= a.maxY;
    boolean z =
        Math.abs(a.minX - b.minX) < 1.0E-6
            && Math.abs(a.maxX - b.maxX) < 1.0E-6
            && Math.abs(a.minY - b.minY) < 1.0E-6
            && Math.abs(a.maxY - b.maxY) < 1.0E-6
            && a.minZ <= b.maxZ
            && b.minZ <= a.maxZ;
    return x || y || z;
  }

  private record SearchEntry(int index, double estimate) {}
}
