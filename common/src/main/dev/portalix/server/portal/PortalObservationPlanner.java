package dev.portalix.server.portal;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalLinkId;
import dev.portalix.portal.PortalViewLimits;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.query.PlayerPortalVisibility;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class PortalObservationPlanner {
  private PortalObservationPlanner() {}

  static Geometry plan(Vec3 eye, Vec3 velocity, int radius, BiFunction<Vec3, Integer, List<PortalLink>> entrances,
      Viewpoint view, List<List<PortalLinkId>> requested, Function<PortalLinkId, PortalLink> resolve) {
    var plan = new Plan(radius);
    var roots = entrances.apply(eye, radius);
    plan.prepareCrossings(eye, velocity, roots);
    if (view != null) {
      if (!view.frame.isEmpty()) {
        var exit = view.frame.getLast().exit().endpoint();
        long center = PortalViewLimits.frontColumn(exit).pack();
        plan.centers.add(center);
        plan.safety(exit);
        plan.simulationCenters.add(center);
        plan.camera = new PlayerPortalVisibility.Observer(view.eye, exit, true);
      }
      if (view.approach != null) {
        var approach = view.approach;
        plan.observe(new Observer(approach.eye, Vec3.ZERO, null), approach.link, true);
      }
      for (var route : requested) plan.demand(view, route, resolve);
    }
    List<Observer> frontier = List.of(new Observer(eye, velocity, null));
    for (int depth = 0; depth < PortalViewLimits.DEPTH && plan.hasRoom(); depth++) {
      var candidates = new ArrayList<ObservationCandidate>();
      for (var observer : frontier) {
        for (var link : depth == 0 ? roots
            : entrances.apply(PortalViewLimits.origin(observer.last.exit().endpoint()), plan.previewRadius)) {
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
    final LongOpenHashSet centers = new LongOpenHashSet();
    final LongOpenHashSet simulationCenters = new LongOpenHashSet();
    final LongOpenHashSet safetyCenters = new LongOpenHashSet();
    final HashSet<PortalEndpoint> safetyPortals = new HashSet<>();
    final LinkedHashMap<ObserverView, Boolean> observations = new LinkedHashMap<>();

    final int previewRadius;

    Plan(int radius) {
      primaryRange = radius * 16;
      double reach = primaryRange + 16;
      reachSquared = reach * reach;
      previewRadius = PortalViewLimits.preview(radius);
    }

    boolean hasRoom() { return observations.size() < PortalViewLimits.ROUTES; }

    boolean reachable(Observer observer, PortalLink link) {
      if (observer.last == null)
        return link.entrance().endpoint().center().distanceToSqr(observer.eye) <= reachSquared;
      return reachableFrom(observer.last, link);
    }

    private boolean reachableFrom(PortalLink previous, PortalLink next) {
      return !previous.inverse().equals(next) && PortalViewLimits.containsEntrance(
          PortalViewLimits.origin(previous.exit().endpoint()), next.entrance().endpoint(), previewRadius);
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
          if (!reachableFrom(links.getLast(), link)) return;
        } else if (camera.frame.isEmpty()) {
          // A detached camera can see beyond the body-centered primary range.
          double reach = primaryRange + Math.max(16, camera.boom);
          if (camera.eye.distanceToSqr(entrance.center()) > reach * reach) return;
        } else {
          var exit = camera.frame.getLast().exit().endpoint();
          if (!PortalViewLimits.containsEntrance(PortalViewLimits.origin(exit), entrance, previewRadius)) return;
        }
        links.add(link);
      }
      var observer = new Observer(camera.eye, camera.velocity, null);
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
      if (!observations.containsKey(view)) {
        if (!hasRoom()) return null;
        observations.put(view, arrival);
        long center = PortalViewLimits.frontColumn(exit).pack();
        simulationCenters.add(center);
        safety(entrance);
        safety(exit);
        if (centers.size() < PortalViewLimits.REGIONS) centers.add(center);
      } else if (!observations.get(view) && arrival) {
        observations.put(view, true);
      }
      return new Observer(virtualEye, link.transform().vector(observer.velocity), link);
    }

    Geometry geometry() {
      var observers = new ArrayList<PlayerPortalVisibility.Observer>(observations.size() + 1);
      if (camera != null) observers.add(camera);
      observations.forEach((view, arrival) -> {
        if (camera == null || !camera.eye().equals(view.eye) || !camera.exit().equals(view.exit))
          observers.add(new PlayerPortalVisibility.Observer(view.eye, view.exit, arrival));
      });
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

  private record Observer(Vec3 eye, Vec3 velocity, PortalLink last) {}

  private record ObserverView(Vec3 eye, PortalEndpoint exit) {}

  private record ObservationCandidate(Observer observer, PortalLink link, boolean arrival, double distanceSquared) {}

  record Geometry(LongOpenHashSet centers, LongOpenHashSet safetyCenters, LongOpenHashSet simulationCenters,
      List<PlayerPortalVisibility.Observer> observers) {}
}
