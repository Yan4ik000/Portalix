package dev.portalix.client.portal.scene;

import static org.junit.jupiter.api.Assertions.*;

import dev.portalix.api.PortalInstance;
import dev.portalix.portal.PortalLink;
import dev.portalix.test.ClientPortalFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

class PortalScenePlannerTest {
  @Test
  void hiddenRootsRemainAvailableForPreparationWithoutExpandingTheirDescendants() {
    var links = new ArrayList<PortalLink>();
    for (int i = 0; i < 2; i++) links.add(link(i + 1, 4 + i * .01, 40, Direction.NORTH));
    var root = root();
    var plans = PortalScenePlanner.plan(
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
    var initial = PortalScenePlanner.plan(first.entrance().endpoint().dimension(), root,
        root.projectionMatrix, root.viewRotationMatrix, links, 128, entrance -> true);
    assertTrue(initial.stream().noneMatch(plan -> plan.path().size() == 2));

    var modelView = new Matrix4f().rotateY((float) Math.PI);
    var plans = PortalScenePlanner.plan(first.entrance().endpoint().dimension(), root,
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
    assertEquals(PortalScreenVisibility.project(second.entrance().endpoint(), camera,
        root.projectionMatrix, camera.viewRotationMatrix, parent.bounds()), child.bounds());
  }

  @Test
  void primaryOcclusionStopsExpansionAndKeepsTheRootPreparationPlan() {
    var first = link(1, -4, -40, Direction.SOUTH);
    var second = link(2, -44, -80, Direction.SOUTH);
    var root = root();
    var links = List.of(first, second);
    var visible = PortalScenePlanner.plan(first.entrance().endpoint().dimension(), root,
        root.projectionMatrix, root.viewRotationMatrix, links, 128, entrance -> true);
    assertTrue(visible.stream().anyMatch(plan -> plan.path().equals(List.of(first, second))));

    var occluded = PortalScenePlanner.plan(first.entrance().endpoint().dimension(), root,
        root.projectionMatrix, root.viewRotationMatrix, links, 128, entrance -> false);
    assertEquals(2, occluded.size());
    assertTrue(occluded.stream().allMatch(plan -> plan.path().size() == 1 && plan.bounds() == null));
  }

  @Test
  void finalProjectionCanRevealARootOutsideThePreliminaryFieldOfView() {
    var portal = link(1, -4, -40, Direction.SOUTH);
    var root = root();
    root.pos = new Vec3(6, 0, 0);
    var initial = PortalScenePlanner.plan(portal.entrance().endpoint().dimension(), root,
        root.projectionMatrix, root.viewRotationMatrix, List.of(portal), 128, entrance -> true);
    assertNull(initial.getFirst().bounds());
    var projection = new Matrix4f().perspective((float) Math.toRadians(140), 1, .05f, 256);
    var finalPlans = PortalScenePlanner.plan(portal.entrance().endpoint().dimension(), root,
        projection, root.viewRotationMatrix, List.of(portal), 128, entrance -> true);
    assertNotNull(finalPlans.stream().filter(plan -> plan.path().equals(List.of(portal)))
        .findFirst().orElseThrow().bounds());
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
