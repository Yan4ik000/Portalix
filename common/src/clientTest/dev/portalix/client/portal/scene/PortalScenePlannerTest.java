package dev.portalix.client.portal.scene;

import static org.junit.jupiter.api.Assertions.*;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.api.PortalInstance;
import dev.portalix.client.portal.clip.PortalCullPlane;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalViewLimits;
import dev.portalix.test.ClientPortalFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

class PortalScenePlannerTest {
  @Test
  void clippedEntrancesLeaveTheRegionBudgetForTheVisibleDescendant() {
    var root = root();
    var first = link(1, -4, -400, Direction.SOUTH);
    var links = List.of(first,
        link(2, -397, -800, Direction.SOUTH), link(3, -398, -900, Direction.SOUTH),
        link(4, -399, -1000, Direction.SOUTH), link(5, -410, -1100, Direction.SOUTH));
    var routes = plan(first.entrance().endpoint().dimension(), root, root.projectionMatrix,
        root.viewRotationMatrix, links, 128, ignored -> true).stream().map(PortalScenePlanner.View::path).toList();
    assertEquals(List.of(List.of(first, links.getLast()), List.of(first)), routes);
  }

  @Test
  void partialExitClippingReducesTheProjectedBounds() {
    var root = root();
    var entrance = ClientPortalFixtures.endpoint(new Vec3(0, 0, -4), Direction.SOUTH);
    var bounds = PortalScreenVisibility.project(entrance, root, root.projectionMatrix,
        PortalScreenVisibility.SCREEN, new PortalCullPlane(1, 0, 0, 0));
    assertNotNull(bounds);
    assertEquals(0, bounds.left(), 1e-6);
    assertEquals(.125, bounds.right(), 1e-6);
  }

  @Test
  void boundsWorkWhenManyPortalsAreOnScreen() {
    var camera = root();
    for (double distance : new double[] {.1, 20}) {
      var links = new ArrayList<PortalLink>();
      for (int i = 0; i < 40; i++) {
        var entrance = ClientPortalFixtures.endpoint(
            new Vec3((i % 8) * 2, (i / 8) * 3, -distance), Direction.SOUTH);
        var exit = ClientPortalFixtures.endpoint(new Vec3(i * 16, 0, -120), Direction.NORTH);
        links.add(new PortalLink(new UUID(0, i + 1), 0,
            new PortalInstance(entrance, 0, i * 2 + 1, null),
            new PortalInstance(exit, 0, i * 2 + 2, null)));
      }
      var plans = plan(links.getFirst().entrance().endpoint().dimension(), camera,
          camera.projectionMatrix, camera.viewRotationMatrix, links, 128, entrance -> true);
      var visible = plans.stream().filter(plan -> plan.bounds() != null).toList();
      long pixels = visible.stream().mapToLong(plan -> PortalSceneViewport.of(plan.bounds(), 1920, 1080).pixels()).sum();
      assertFalse(visible.isEmpty());
      assertTrue(visible.size() <= PortalViewLimits.ROUTES);
      assertTrue(visible.stream().map(plan -> SectionPos.of(PortalViewLimits.origin(
          plan.path().getLast().exit().endpoint())).chunk()).distinct().count() <= PortalViewLimits.REGIONS);
      assertTrue(pixels <= (long) PortalScenePlanner.SCREEN_AREA_LIMIT * 1920 * 1080);
    }
  }

  @Test
  void hiddenRootsRemainAvailableForPreparationWithoutExpandingTheirDescendants() {
    var links = new ArrayList<PortalLink>();
    for (int i = 0; i < 2; i++) links.add(link(i + 1, 4 + i * .01, 40, Direction.NORTH));
    var root = root();
    var plans = plan(
        links.getFirst().entrance().endpoint().dimension(), root, root.projectionMatrix,
        root.viewRotationMatrix, links, 128, entrance -> true);

    assertEquals(links.size(), plans.size());
    for (var plan : plans) {
      assertEquals(1, plan.path().size());
      assertNull(plan.bounds());
      assertEquals(plan.path().getFirst().transform().point(root.pos), plan.position());
    }
  }

  @Test
  void finalViewEffectsRecoverVisibleDescendantsOfAPreviouslyHiddenRoot() {
    var first = link(1, 4, 40, Direction.NORTH);
    var second = link(2, 44, 80, Direction.NORTH);
    var links = List.of(first, first.inverse(), second, second.inverse());
    var root = root();
    var initial = plan(first.entrance().endpoint().dimension(), root,
        root.projectionMatrix, root.viewRotationMatrix, links, 128, entrance -> true);
    assertTrue(initial.stream().noneMatch(plan -> plan.path().size() == 2));

    var modelView = new Matrix4f().rotateY((float) Math.PI);
    var plans = plan(first.entrance().endpoint().dimension(), root,
        root.projectionMatrix, modelView, links, 128, entrance -> true);
    var parent = plans.stream().filter(plan -> plan.path().equals(List.of(first))).findFirst().orElseThrow();
    var child = plans.stream().filter(plan -> plan.path().equals(List.of(first, second))).findFirst().orElseThrow();
    assertNotNull(parent.bounds());
    assertNotNull(child.bounds());
    assertTrue(plans.indexOf(child) < plans.indexOf(parent), "children render before their parents");
    assertTrue(plans.stream().noneMatch(plan -> plan.path().equals(List.of(first, first.inverse()))));

    var camera = new CameraRenderState();
    camera.pos = first.transform().point(root.pos);
    camera.orientation.set(first.transform().rotation().mul(root.orientation));
    camera.viewRotationMatrix.rotation(new Quaternionf(camera.orientation).conjugate()).mulLocal(modelView);
    var viewProjection = new Matrix4f(root.projectionMatrix).mul(camera.viewRotationMatrix);
    assertEquals(PortalScreenVisibility.project(second.entrance().endpoint(), camera,
        viewProjection, parent.bounds(), null), child.bounds());
  }

  @Test
  void nestedViewIncludesTheOuterColumnOnTheOpenSideOfAChunkBoundary() {
    var first = link(1, -4, -400, Direction.SOUTH);
    var exit = first.exit().endpoint();
    var origin = PortalViewLimits.origin(exit);
    var last = origin;
    while (PortalViewLimits.contains(origin, last.add(0, 0, -16), 2)) last = last.add(0, 0, -16);
    assertFalse(PortalViewLimits.contains(exit.center(), last, 2));
    var second = link(2, last.z, -600, Direction.SOUTH);
    var camera = root();
    var plans = plan(exit.dimension(), camera, camera.projectionMatrix,
        camera.viewRotationMatrix, List.of(first, second), 32, entrance -> true);
    assertTrue(plans.stream().anyMatch(plan -> plan.path().equals(List.of(first, second)) && plan.bounds() != null));
  }

  @Test
  void nestedViewUsesTheRadiusOfThePortalItIsSeenThrough() {
    var first = link(1, -4, -400, Direction.SOUTH);
    var second = link(2, -500, -800, Direction.SOUTH);
    var camera = root();
    for (int radius : new int[] {4, 8}) {
      var plans = PortalScenePlanner.plan(first.entrance().endpoint().dimension(), camera,
          camera.projectionMatrix, camera.viewRotationMatrix, (position, distance) -> List.of(first, second),
          128, entrance -> true, 1920, 1080, PortalViewLimits.ROUTES, camera.pos, null,
          link -> link.equals(first) ? radius : 16);
      assertEquals(radius == 8, plans.stream().anyMatch(plan -> plan.path().equals(List.of(first, second))));
    }
  }

  @Test
  void skipsNestedEntranceAboveTheDestinationTerrainWindow() {
    var entrance = ClientPortalFixtures.endpoint(new Vec3(0, 0, -4), Direction.SOUTH);
    var floor = ClientPortalFixtures.endpoint(new Vec3(1000, 0, 0), Direction.UP);
    var ceiling = ClientPortalFixtures.endpoint(new Vec3(1000, 190, 0), Direction.DOWN);
    var exit = ClientPortalFixtures.endpoint(new Vec3(2000, 0, 0), Direction.SOUTH);
    var first = new PortalLink(new UUID(0, 1), 0,
        new PortalInstance(entrance, 0, 1, null), new PortalInstance(floor, 0, 2, null));
    var second = new PortalLink(new UUID(0, 2), 0,
        new PortalInstance(ceiling, 0, 3, null), new PortalInstance(exit, 0, 4, null));
    var camera = root();
    var plans = plan(entrance.dimension(), camera, camera.projectionMatrix,
        camera.viewRotationMatrix, List.of(first, second), 128, ignored -> true);
    assertTrue(plans.stream().anyMatch(plan -> plan.path().equals(List.of(first)) && plan.bounds() != null));
    assertFalse(plans.stream().anyMatch(plan -> plan.path().contains(second)));
    assertTrue(PortalViewLimits.containsEntrance(PortalViewLimits.origin(floor),
        ClientPortalFixtures.endpoint(new Vec3(1000, 144.5, 0), Direction.SOUTH), 8));
  }

  @Test
  void primaryOcclusionStopsExpansionAndKeepsTheRootPreparationPlan() {
    var first = link(1, -4, -40, Direction.SOUTH);
    var second = link(2, -44, -80, Direction.SOUTH);
    var root = root();
    var links = List.of(first, second);
    var visible = plan(first.entrance().endpoint().dimension(), root,
        root.projectionMatrix, root.viewRotationMatrix, links, 128, entrance -> true);
    assertTrue(visible.stream().anyMatch(plan -> plan.path().equals(List.of(first, second))));

    var occluded = plan(first.entrance().endpoint().dimension(), root,
        root.projectionMatrix, root.viewRotationMatrix, links, 128, entrance -> false);
    assertEquals(2, occluded.size());
    assertTrue(occluded.stream().allMatch(plan -> plan.path().size() == 1 && plan.bounds() == null));
  }

  @Test
  void finalProjectionCanRevealARootOutsideThePreliminaryFieldOfView() {
    var portal = link(1, -4, -40, Direction.SOUTH);
    var root = root();
    root.pos = new Vec3(6, 0, 0);
    var initial = plan(portal.entrance().endpoint().dimension(), root,
        root.projectionMatrix, root.viewRotationMatrix, List.of(portal), 128, entrance -> true);
    assertNull(initial.getFirst().bounds());
    var projection = new Matrix4f().perspective((float) Math.toRadians(140), 1, .05f, 256);
    var finalPlans = plan(portal.entrance().endpoint().dimension(), root,
        projection, root.viewRotationMatrix, List.of(portal), 128, entrance -> true);
    assertNotNull(finalPlans.stream().filter(plan -> plan.path().equals(List.of(portal)))
        .findFirst().orElseThrow().bounds());
  }

  private static List<PortalScenePlanner.View> plan(
      ResourceKey<Level> dimension, CameraRenderState root, Matrix4fc projection, Matrix4fc modelView,
      List<PortalLink> links, double range, Predicate<PortalEndpoint> primaryVisible) {
    return PortalScenePlanner.plan(dimension, root, projection, modelView,
        (position, distance) -> links, range, primaryVisible,
        1920, 1080, PortalViewLimits.ROUTES, root.pos, null,
        link -> PortalViewLimits.preview((int) Math.ceil(range / 16)));
  }

  private static CameraRenderState root() {
    var root = new CameraRenderState();
    root.pos = Vec3.ZERO;
    root.projectionMatrix.perspective((float) Math.toRadians(90), 1, .05f, 256);
    return root;
  }

  private static PortalLink link(long id, double entranceZ, double exitZ, Direction normal) {
    var entrance = ClientPortalFixtures.endpoint(new Vec3(0, 0, entranceZ), normal);
    var exit = ClientPortalFixtures.endpoint(new Vec3(0, 0, exitZ), normal.getOpposite());
    return new PortalLink(new UUID(0, id), 0,
        new PortalInstance(entrance, 0, id * 2, null), new PortalInstance(exit, 0, id * 2 + 1, null));
  }
}
