package dev.portalix.server.portal;

import static dev.portalix.test.PortalFixtures.endpoint;
import static dev.portalix.test.PortalFixtures.link;
import static org.junit.jupiter.api.Assertions.*;

import dev.portalix.api.PortalInstance;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalLinkId;
import dev.portalix.portal.PortalViewLimits;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.test.MinecraftTestBootstrap;
import dev.portalix.world.chunk.ChunkViewUnion;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PortalObservationPlannerTest {
  @BeforeAll
  static void bootstrapMinecraft() {
    MinecraftTestBootstrap.initialize();
  }

  @Test
  void distantRegionsStayBoundedAndTheApproachingPortalKeepsItsCore() {
    var links = new ArrayList<PortalLink>();
    var normal = new Vec3(0, 0, -1);
    var up = new Vec3(0, 1, 0);
    for (int i = 0; i < 40; i++) {
      var entrance = endpoint(new Vec3(i, 100, 10 + i), normal, up);
      var exit = endpoint(new Vec3(1024 + i * 1024, 100, 1024), normal.scale(-1), up);
      links.add(link(entrance, exit));
    }
    Vec3 eye = new Vec3(0, 100, 8);
    for (int radius : new int[] {2, 16, 32}) {
      var geometry = plan(eye, new Vec3(0, 0, .2), radius, (point, radiusInChunks) -> links,
          new PortalObservationPlanner.Viewpoint(eye, Vec3.ZERO, List.of(), 4), List.of(), id -> null);
      assertEquals(PortalViewLimits.REGIONS, geometry.centers().size());
      assertEquals(PortalViewLimits.ROUTES, geometry.observers().size());
      assertTrue(geometry.simulationCenters().size() <= PortalViewLimits.ROUTES);
      var expected = links.getFirst().exit().endpoint();
      assertTrue(geometry.safetyCenters().contains(PortalViewLimits.frontColumn(expected).pack()));
      assertTrue(geometry.observers().stream().anyMatch(observer -> observer.exit().equals(expected) && observer.arrival()));
      assertTrue(geometry.safetyCenters().size() <= 8 * PortalViewLimits.ROUTES);
    }
  }

  @Test
  void aLongDetachedCameraCanRequestVisibleRootsBeyondThePrimaryRadius() {
    var entrance = endpoint(new Vec3(0, 95, 0), new Vec3(0, -1, 0), new Vec3(0, 0, 1));
    var exit = endpoint(new Vec3(1000, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1));
    var portal = link(entrance, exit);
    long center = PortalViewLimits.frontColumn(exit).pack();
    var id = PortalLinkId.fromLink(portal);
    for (int boom : new int[] {4, 62, 64}) {
      var view = new PortalObservationPlanner.Viewpoint(Vec3.ZERO, Vec3.ZERO, List.of(), boom);
      var plan = plan(Vec3.ZERO, Vec3.ZERO, 2, (point, radiusInChunks) -> List.of(), view,
          List.of(List.of(id)), ignored -> portal);
      assertEquals(32 + boom >= 95, plan.centers().containsKey(center));
    }
  }

  @Test
  void selectedViewsKeepTheirRegionsAheadOfHiddenPreparation() {
    var links = new ArrayList<PortalLink>();
    var up = new Vec3(0, 1, 0);
    for (int i = 0; i < 5; i++) {
      var entrance = endpoint(new Vec3(0, 0, i == 4 ? -20 : 5 + i), new Vec3(0, 0, i == 4 ? 1 : -1), up);
      var exit = endpoint(new Vec3((i + 1) * 10000, 0, -1000), entrance.normal().scale(-1), up);
      links.add(new PortalLink(new UUID(0, i + 1), 1,
          new PortalInstance(entrance, 1, i * 2 + 1, null),
          new PortalInstance(exit, 1, i * 2 + 2, null)));
    }
    var selected = links.getLast();
    var id = PortalLinkId.fromLink(selected);
    Function<PortalLinkId, PortalLink> resolve = key -> links.stream()
        .filter(link -> PortalLinkId.fromLink(link).equals(key)).findFirst().orElse(null);
    var root = new PortalObservationPlanner.Viewpoint(Vec3.ZERO, Vec3.ZERO, List.of(), 4);
    var geometry = plan(Vec3.ZERO, Vec3.ZERO, 16, (point, radiusInChunks) -> links,
        root, List.of(List.of(id)), resolve);
    assertEquals(PortalViewLimits.REGIONS, geometry.centers().size());
    assertTrue(geometry.centers().containsKey(PortalViewLimits.frontColumn(selected.exit().endpoint()).pack()));
    assertTrue(geometry.safetyCenters().contains(PortalViewLimits.frontColumn(links.getFirst().exit().endpoint()).pack()));
    assertTrue(geometry.observers().size() <= PortalViewLimits.ROUTES);
    var stale = new PortalLinkId(id.gun(), id.generation() + 1, id.entrance(), id.exit());
    var rejected = plan(Vec3.ZERO, Vec3.ZERO, 16, (point, radiusInChunks) -> links,
        root, List.of(List.of(stale)), resolve);
    assertFalse(rejected.centers().containsKey(PortalViewLimits.frontColumn(selected.exit().endpoint()).pack()));
    var requests = links.stream().map(link -> List.of(PortalLinkId.fromLink(link))).toList();
    var fitting = requests.subList(0, PortalViewLimits.REGIONS);
    assertTrue(PortalObservationPlanner.sameDemand(fitting, fitting.reversed(), List.of(), resolve));
    assertFalse(PortalObservationPlanner.sameDemand(requests, requests.reversed(), List.of(), resolve));
    var firstOrder = plan(Vec3.ZERO, Vec3.ZERO, 16,
        (point, radius) -> List.of(), root, requests, resolve);
    var reversedOrder = plan(Vec3.ZERO, Vec3.ZERO, 16,
        (point, radius) -> List.of(), root, requests.reversed(), resolve);
    assertFalse(firstOrder.centers().containsKey(PortalViewLimits.frontColumn(selected.exit().endpoint()).pack()));
    assertTrue(reversedOrder.centers().containsKey(PortalViewLimits.frontColumn(selected.exit().endpoint()).pack()));
  }

  @Test
  void remoteCameraAndNearbyCrossingsRemainPreparedWhenViewSlotsAreFull() {
    var frame = link(endpoint(new Vec3(0, 0, -2), new Vec3(0, 0, 1), new Vec3(0, 1, 0)),
        endpoint(new Vec3(10000, 0, 0), new Vec3(0, 0, -1), new Vec3(0, 1, 0)));
    var eye = frame.transform().point(Vec3.ZERO);
    var requested = new ArrayList<List<PortalLinkId>>();
    var links = new ArrayList<PortalLink>();
    for (int index = 0; index < PortalViewLimits.ROUTES; index++) {
      var entrance = endpoint(eye.add(index, 0, -10), new Vec3(0, 0, 1), new Vec3(0, 1, 0));
      var exit = endpoint(new Vec3(20000 + index * 1000, 0, 0), new Vec3(0, 0, -1), new Vec3(0, 1, 0));
      var portal = new PortalLink(new UUID(1, index), 1,
          new PortalInstance(entrance, 1, index * 2 + 1, null),
          new PortalInstance(exit, 1, index * 2 + 2, null));
      links.add(portal);
      requested.add(List.of(PortalLinkId.fromLink(portal)));
    }
    var nearby = link(endpoint(new Vec3(0, 0, 2), new Vec3(0, 0, -1), new Vec3(0, 1, 0)),
        endpoint(new Vec3(-30000, 0, 0), new Vec3(0, 0, 1), new Vec3(0, 1, 0)));
    var geometry = plan(Vec3.ZERO, Vec3.ZERO, 16, (point, radiusInChunks) -> List.of(nearby),
        new PortalObservationPlanner.Viewpoint(eye, Vec3.ZERO, List.of(frame), 4), requested,
        id -> links.stream().filter(link -> PortalLinkId.fromLink(link).equals(id)).findFirst().orElse(null));
    assertEquals(PortalViewLimits.ROUTES + 1, geometry.observers().size());
    assertEquals(eye, geometry.observers().getFirst().eye());
    assertTrue(geometry.safetyCenters().contains(PortalViewLimits.frontColumn(nearby.exit().endpoint()).pack()));
    assertFalse(geometry.centers().containsKey(ChunkPos.pack(BlockPos.containing(nearby.transform().point(Vec3.ZERO)))));
    assertTrue(geometry.centers().containsKey(PortalViewLimits.frontColumn(frame.exit().endpoint()).pack()));
    assertEquals(PortalViewLimits.REGIONS, geometry.centers().size());
    assertTrue(geometry.simulationCenters().contains(PortalViewLimits.frontColumn(frame.exit().endpoint()).pack()));
  }

  @Test
  void distantApertureAndNestedRequestsUseTheExitAsTheirOrigin() {
    var up = new Vec3(0, 1, 0);
    var normal = new Vec3(0, 0, 1);
    var first = link(endpoint(new Vec3(0, 0, 320), normal.scale(-1), up),
        endpoint(new Vec3(10000, 0, 0), normal, up));
    var exit = first.exit().endpoint().center();
    var beyond = link(endpoint(exit.add(0, 0, 240), normal.scale(-1), up),
        endpoint(new Vec3(25000, 0, 0), normal, up));
    var eye = first.transform().point(Vec3.ZERO);
    var geometry = plan(Vec3.ZERO, Vec3.ZERO, 32, (point, radiusInChunks) -> {
      assertEquals(point.equals(Vec3.ZERO) ? 32 : 8, radiusInChunks);
      return List.of(first, beyond);
    },
        new PortalObservationPlanner.Viewpoint(Vec3.ZERO, Vec3.ZERO, List.of(), 4), List.of(), id -> null);
    assertTrue(geometry.centers().containsKey(ChunkPos.pack(BlockPos.containing(exit))));
    assertFalse(geometry.centers().containsKey(ChunkPos.pack(BlockPos.containing(eye))));
    assertFalse(geometry.centers().containsKey(PortalViewLimits.frontColumn(beyond.exit().endpoint()).pack()));

    var id = PortalLinkId.fromLink(beyond);
    var camera = new PortalObservationPlanner.Viewpoint(exit.add(0, 0, 112), Vec3.ZERO, List.of(first), 4);
    var request = plan(Vec3.ZERO, Vec3.ZERO, 32, (point, radiusInChunks) -> List.of(),
        camera, List.of(List.of(id)), ignored -> beyond);
    assertTrue(request.centers().containsKey(ChunkPos.pack(BlockPos.containing(exit))));
    assertFalse(request.centers().containsKey(ChunkPos.pack(BlockPos.containing(camera.eye()))));
    assertFalse(request.centers().containsKey(PortalViewLimits.frontColumn(beyond.exit().endpoint()).pack()));
  }

  @Test
  void remoteDemandAndNestedPreparationUseTheHeightOfTheExitWindow() {
    var up = new Vec3(0, 1, 0);
    var normal = new Vec3(0, 0, 1);
    var first = link(endpoint(Vec3.ZERO, normal, up), endpoint(new Vec3(10000, 0, 0), normal.scale(-1), up));
    var exit = first.exit().endpoint();
    var distant = endpoint(new Vec3(30000, 0, 0), normal.scale(-1), up);
    var key = PortalViewLimits.frontColumn(distant).pack();
    // The upper face of section 8 is y=144. A wall aperture extends one block below its center.
    for (double height : new double[] {144.5, 145}) {
      var nested = link(endpoint(exit.center().add(0, height, 4), normal, up), distant);
      boolean intersects = height == 144.5;
      var prepared = plan(Vec3.ZERO, Vec3.ZERO, 32,
          (point, radiusInChunks) -> List.of(first, nested), null, List.of(), id -> null);
      assertEquals(intersects, prepared.centers().containsKey(key));

      // Even with the virtual camera at that height, demand is bounded by the physical exit.
      var camera = new PortalObservationPlanner.Viewpoint(exit.center().add(0, height, 0), Vec3.ZERO, List.of(first), 4);
      var requested = plan(Vec3.ZERO, Vec3.ZERO, 32, (point, radiusInChunks) -> List.of(),
          camera, List.of(List.of(PortalLinkId.fromLink(nested))), id -> nested);
      assertEquals(intersects, requested.centers().containsKey(key));
    }
  }

  @Test
  void anApertureCanOverlapTheLastColumnWithItsCenterOutside() {
    var up = new Vec3(0, 1, 0);
    var back = new Vec3(0, 0, -1);
    var first = link(endpoint(new Vec3(-10000, 0, 8), back, up), endpoint(new Vec3(0, 0, 8), back.scale(-1), up));
    var origin = PortalViewLimits.origin(first.exit().endpoint());
    var destination = endpoint(new Vec3(10000, 0, 8), back.scale(-1), up);
    for (double x : new double[] {144.1, 144.5}) {
      var nested = link(endpoint(new Vec3(x, 0, 8), back, up), destination);
      assertFalse(PortalViewLimits.contains(origin, nested.entrance().endpoint().center(), 8));
      boolean overlaps = x < 144.5;
      var demand = plan(first.entrance().endpoint().center(), Vec3.ZERO,
          8, (point, radiusInChunks) -> List.of(), new PortalObservationPlanner.Viewpoint(origin, Vec3.ZERO, List.of(first), 4),
          List.of(List.of(PortalLinkId.fromLink(nested))), ignored -> nested);
      assertEquals(overlaps, demand.centers().containsKey(PortalViewLimits.frontColumn(destination).pack()));
    }
    var onEdge = endpoint(new Vec3(144, 0, 8), new Vec3(-1, 0, 0), up);
    assertTrue(PortalViewLimits.containsEntrance(origin, onEdge, 8));
  }

  @Test
  void equalVirtualEyesRetainTheDistinctExitsForNestedViews() {
    var front = new Vec3(0, 0, 1);
    var back = front.scale(-1);
    var up = new Vec3(0, 1, 0);
    var first = link(endpoint(Vec3.ZERO, front, up), endpoint(new Vec3(10000, 0, 0), back, up));
    var second = link(endpoint(new Vec3(240, 0, 0), front, up), endpoint(new Vec3(10240, 0, 0), back, up));
    var nested = link(endpoint(new Vec3(10244, 0, 0), front, up), endpoint(new Vec3(30000, 0, 0), back, up));
    var eye = new Vec3(4, 0, 4);
    assertEquals(first.transform().point(eye), second.transform().point(eye));
    var geometry = plan(eye, Vec3.ZERO, 32,
        (point, radiusInChunks) -> List.of(first, second, nested),
        new PortalObservationPlanner.Viewpoint(eye, Vec3.ZERO, List.of(), 4), List.of(), id -> null);
    assertTrue(geometry.centers().containsKey(PortalViewLimits.frontColumn(nested.exit().endpoint()).pack()));
  }

  @Test
  void exitOnANegativeFaceUsesTheColumnInFrontForDemandSimulationAndReadiness() {
    var up = new Vec3(0, 1, 0);
    var entrance = endpoint(new Vec3(4, 5, 4), new Vec3(1, 0, 0), up);
    var exit = endpoint(new Vec3(-32, 5, 8), new Vec3(-1, 0, 0), up);
    var route = link(entrance, exit);
    var geometry = plan(entrance.center(), Vec3.ZERO, 8, (point, radiusInChunks) -> List.of(route),
        new PortalObservationPlanner.Viewpoint(entrance.center(), Vec3.ZERO, List.of(), 4), List.of(), ignored -> null);
    long front = ChunkPos.pack(-3, 0);
    assertTrue(geometry.centers().containsKey(front));
    assertTrue(geometry.safetyCenters().contains(front));
    assertTrue(geometry.simulationCenters().contains(front));
    assertFalse(geometry.centers().containsKey(ChunkPos.pack(-2, 0)));
    var delivered = new LongOpenHashSet();
    for (int x = -3; x <= -1; x++)
      for (int z = -1; z <= 1; z++) delivered.add(ChunkPos.pack(x, z));
    assertFalse(PortalViewLimits.hasExitCore(exit, delivered::contains));
    for (int z = -1; z <= 1; z++) delivered.add(ChunkPos.pack(-4, z));
    assertTrue(PortalViewLimits.hasExitCore(exit, delivered::contains));
    var state = new PlayerPortalVisibility();
    state.ready(route);
    state.forgetColumn(ChunkPos.pack(-1, 0));
    assertTrue(state.isReady(route));
    state.forgetColumn(ChunkPos.pack(-4, 0));
    assertFalse(state.isReady(route));
  }
  @Test
  void eachExitDeliversTheRadiusOfItsOwnEntrance() {
    var direction = new Vec3(0, 0, -1);
    var up = new Vec3(0, 1, 0);
    var near = link(endpoint(new Vec3(0, 5, 8), direction, up),
        endpoint(new Vec3(1024, 5, 64), direction, up));
    var far = link(endpoint(new Vec3(16, 5, 8), direction, up),
        endpoint(new Vec3(2048, 5, 64), direction, up));
    var geometry = PortalObservationPlanner.plan(Vec3.ZERO, Vec3.ZERO, 32, link -> link == near ? 4 : 18,
        (point, radius) -> List.of(near, far), null, List.of(), id -> null);
    var shortCenter = PortalViewLimits.frontColumn(near.exit().endpoint());
    var longCenter = PortalViewLimits.frontColumn(far.exit().endpoint());
    assertEquals(4, geometry.centers().get(shortCenter.pack()));
    assertEquals(18, geometry.centers().get(longCenter.pack()));
    var columns = ChunkViewUnion.collect(geometry.centers());
    assertFalse(columns.contains(ChunkPos.pack(shortCenter.x() + 12, shortCenter.z())));
    assertTrue(columns.contains(ChunkPos.pack(longCenter.x() + 12, longCenter.z())));
  }

  private static PortalObservationPlanner.Geometry plan(Vec3 eye, Vec3 velocity, int radius,
      BiFunction<Vec3, Integer, List<PortalLink>> entrances, PortalObservationPlanner.Viewpoint view,
      List<List<PortalLinkId>> requested, Function<PortalLinkId, PortalLink> resolve) {
    return PortalObservationPlanner.plan(eye, velocity, radius, link -> PortalViewLimits.preview(radius),
        entrances, view, requested, resolve);
  }

}
