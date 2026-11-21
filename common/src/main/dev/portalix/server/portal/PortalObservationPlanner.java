package dev.portalix.server.portal;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalLinkId;
import dev.portalix.portal.PortalViewLimits;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.query.PlayerPortalVisibility;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class PortalObservationPlanner {
  private PortalObservationPlanner() {}

  static Geometry plan(Vec3 eye, Vec3 velocity, int radius, ToIntFunction<PortalLink> portalRadius,
      BiFunction<Vec3, Integer, List<PortalLink>> entrances, Viewpoint view,
      List<List<PortalLinkId>> requested, Function<PortalLinkId, PortalLink> resolve) {
    var plan = new Plan(radius, portalRadius);
    var roots = entrances.apply(eye, radius);
    plan.prepareCrossings(eye, velocity, roots);
    if (view != null) {
      if (!view.frame.isEmpty()) {
        var link = view.frame.getLast();
        var exit = link.exit().endpoint();
        long center = PortalViewLimits.frontColumn(exit).pack();
        int cameraRadius = portalRadius.applyAsInt(link);
        plan.addCenter(center, cameraRadius);
        plan.safety(exit);
        plan.simulationCenters.add(center);
        plan.camera = new PlayerPortalVisibility.Observer(view.eye, exit, true, cameraRadius);
      }
      if (view.approach != null) {
        var approach = view.approach;
        plan.observe(new Observer(approach.eye, Vec3.ZERO, null, 0), approach.link, true);
      }
      for (var route : requested) plan.demand(view, route, resolve);
    }
    List<Observer> frontier = List.of(new Observer(eye, velocity, null, 0));
    for (int depth = 0; depth < PortalViewLimits.DEPTH && plan.hasRoom(); depth++) {
      var candidates = new ArrayList<ObservationCandidate>();
      for (var observer : frontier) {
        for (var link : depth == 0 ? roots
            : entrances.apply(PortalViewLimits.origin(observer.last.exit().endpoint()),
                observer.radius)) {
          if (!plan.reachable(observer, link)) continue;
          candidates.add(new ObservationCandidate(observer, link,
              arriving(link.entrance().endpoint(), observer.eye, observer.velocity),
              link.entrance().endpoint().center().distanceToSqr(observer.eye)));
        }
      }
      candidates.sort(Comparator.comparing(ObservationCandidate::arrival).reversed()
          .thenComparingDouble(ObservationCandidate::distanceSquared));
      var next = new LinkedHashSet<Observer>();
      for (var candidate : candidates) {
        if (!plan.hasRoom()) break;
        next.add(plan.observe(candidate.observer(), candidate.link()));
      }
      frontier = List.copyOf(next);
    }
    return plan.geometry();
  }

  static boolean sameDemand(List<List<PortalLinkId>> previous, List<List<PortalLinkId>> next,
      List<PortalLink> cameraFrame, Function<PortalLinkId, PortalLink> resolve) {
    if (previous.equals(next)) return true;
    if (!previous.containsAll(next) || !next.containsAll(previous)) return false;
    var regions = new LongOpenHashSet();
    if (!cameraFrame.isEmpty())
      regions.add(PortalViewLimits.frontColumn(cameraFrame.getLast().exit().endpoint()).pack());
    var paths = new HashSet<List<PortalLinkId>>();
    for (var path : next) {
      for (int length = 1; length <= path.size(); length++) {
        var prefix = path.subList(0, length);
        var link = resolve.apply(prefix.getLast());
        if (link == null) break;
        paths.add(prefix);
        regions.add(PortalViewLimits.frontColumn(link.exit().endpoint()).pack());
        if (paths.size() > PortalViewLimits.ROUTES || regions.size() > PortalViewLimits.REGIONS) return false;
      }
    }
    return true;
  }

  private static final class Plan {
    final int primaryRange;
    final double reachSquared;
    PlayerPortalVisibility.Observer camera;
    final Long2IntMap centers = new Long2IntOpenHashMap();
    final LongOpenHashSet simulationCenters = new LongOpenHashSet();
    final LongOpenHashSet safetyCenters = new LongOpenHashSet();
    final HashSet<PortalEndpoint> safetyPortals = new HashSet<>();
    final LinkedHashMap<ObserverView, PlayerPortalVisibility.Observer> observations = new LinkedHashMap<>();
    final ToIntFunction<PortalLink> portalRadius;

    Plan(int radius, ToIntFunction<PortalLink> portalRadius) {
      primaryRange = radius * 16;
      double reach = primaryRange + 16;
      reachSquared = reach * reach;
      this.portalRadius = portalRadius;
    }

    boolean hasRoom() { return observations.size() < PortalViewLimits.ROUTES; }

    boolean reachable(Observer observer, PortalLink link) {
      if (observer.last == null)
        return link.entrance().endpoint().center().distanceToSqr(observer.eye) <= reachSquared;
      return reachableFrom(observer.last, link, observer.radius);
    }

    private static boolean reachableFrom(PortalLink previous, PortalLink next, int radius) {
      return !previous.inverse().equals(next) && PortalViewLimits.containsEntrance(
          PortalViewLimits.origin(previous.exit().endpoint()), next.entrance().endpoint(), radius);
    }

    void prepareCrossings(Vec3 eye, Vec3 velocity, List<PortalLink> roots) {
      roots.stream().filter(link -> link.entrance().endpoint().center().distanceToSqr(eye) <= reachSquared
          && arriving(link.entrance().endpoint(), eye, velocity))
          .sorted(Comparator.comparingDouble(link -> link.entrance().endpoint().center().distanceToSqr(eye)))
          .limit(PortalViewLimits.ROUTES).forEach(link -> {
            safety(link.entrance().endpoint());
            safety(link.exit().endpoint());
            simulationCenters.add(PortalViewLimits.frontColumn(link.exit().endpoint()).pack());
          });
    }

    void demand(Viewpoint camera, List<PortalLinkId> route, Function<PortalLinkId, PortalLink> resolve) {
      var links = new ArrayList<PortalLink>(route.size());
      for (var id : route) {
        var link = resolve.apply(id);
        if (link == null) return;
        var entrance = link.entrance().endpoint();
        if (!links.isEmpty()) {
          var previous = links.getLast();
          if (!reachableFrom(previous, link, portalRadius.applyAsInt(previous))) return;
        } else if (camera.frame.isEmpty()) {
          // A detached camera can see beyond the body-centered primary range.
          double reach = primaryRange + Math.max(16, camera.boom);
          if (camera.eye.distanceToSqr(entrance.center()) > reach * reach) return;
        } else {
          var previous = camera.frame.getLast();
          if (!PortalViewLimits.containsEntrance(
              PortalViewLimits.origin(previous.exit().endpoint()), entrance, portalRadius.applyAsInt(previous))) return;
        }
        links.add(link);
      }
      var observer = new Observer(camera.eye, camera.velocity, null, 0);
      for (var link : links) {
        observer = observe(observer, link);
        if (observer == null) return;
      }
    }

    void safety(PortalEndpoint endpoint) {
      if (safetyPortals.add(endpoint)) addSafetyCenters(safetyCenters, endpoint);
    }

    Observer observe(Observer observer, PortalLink link) {
      return observe(observer, link, arriving(link.entrance().endpoint(), observer.eye, observer.velocity));
    }

    Observer observe(Observer observer, PortalLink link, boolean arrival) {
      var entrance = link.entrance().endpoint();
      var exit = link.exit().endpoint();
      var virtualEye = link.transform().point(observer.eye);
      var view = new ObserverView(virtualEye, exit);
      var previous = observations.get(view);
      int radius = portalRadius.applyAsInt(link);
      long center = PortalViewLimits.frontColumn(exit).pack();
      if (previous == null) {
        if (!hasRoom()) return null;
        observations.put(view, new PlayerPortalVisibility.Observer(virtualEye, exit, arrival, radius));
        simulationCenters.add(center);
        safety(entrance);
        safety(exit);
      } else if ((!previous.arrival() && arrival) || previous.radius() < radius) {
        observations.put(view, new PlayerPortalVisibility.Observer(
            virtualEye, exit, previous.arrival() || arrival, Math.max(previous.radius(), radius)));
      }
      addCenter(center, radius);
      return new Observer(virtualEye, link.transform().vector(observer.velocity), link, radius);
    }

    void addCenter(long center, int radius) {
      int previous = centers.get(center);
      if (radius > previous && (previous != 0 || centers.size() < PortalViewLimits.REGIONS))
        centers.put(center, radius);
    }

    Geometry geometry() {
      var observers = new ArrayList<PlayerPortalVisibility.Observer>(observations.size() + 1);
      if (camera != null) observers.add(camera);
      for (var observer : observations.values()) {
        if (camera == null || !camera.eye().equals(observer.eye()) || !camera.exit().equals(observer.exit()))
          observers.add(observer);
      }
      return new Geometry(centers, safetyCenters, simulationCenters, observers);
    }
  }

  private static void addSafetyCenters(LongOpenHashSet centers, PortalEndpoint portal) {
    var front = PortalViewLimits.origin(portal);
    var extent = PortalAperture.halfExtents(portal);
    int minX = SectionPos.posToSectionCoord(front.x - extent.x);
    int maxX = SectionPos.posToSectionCoord(front.x + extent.x);
    int minZ = SectionPos.posToSectionCoord(front.z - extent.z);
    int maxZ = SectionPos.posToSectionCoord(front.z + extent.z);
    for (int x = minX; x <= maxX; x++)
      for (int z = minZ; z <= maxZ; z++) centers.add(ChunkPos.pack(x, z));
  }

  private static boolean arriving(PortalEndpoint entrance, Vec3 eye, Vec3 velocity) {
    var relative = eye.subtract(entrance.center());
    double normal = PortalAperture.signedDistance(entrance, eye);
    double right = Math.max(0, Math.abs(relative.dot(entrance.right())) - entrance.width() / 2);
    double up = Math.max(0, Math.abs(relative.dot(entrance.up())) - entrance.height() / 2);
    double approachSpeed = Math.max(0, -velocity.dot(entrance.normal())) * 20;
    double reach = Math.max(entrance.width(), entrance.height()) * 2 + approachSpeed;
    return normal * normal + right * right + up * up <= reach * reach;
  }

  record Viewpoint(Vec3 eye, Vec3 velocity, List<PortalLink> frame, double boom,
      @Nullable CameraApproach approach) {
    Viewpoint(Vec3 eye, Vec3 velocity, List<PortalLink> frame, double boom) {
      this(eye, velocity, frame, boom, null);
    }
  }

  record CameraApproach(Vec3 eye, PortalLink link) {}

  private record Observer(Vec3 eye, Vec3 velocity, PortalLink last, int radius) {}

  private record ObserverView(Vec3 eye, PortalEndpoint exit) {}

  private record ObservationCandidate(Observer observer, PortalLink link, boolean arrival, double distanceSquared) {}

  record Geometry(Long2IntMap centers, LongOpenHashSet safetyCenters, LongOpenHashSet simulationCenters,
      List<PlayerPortalVisibility.Observer> observers) {}
}
