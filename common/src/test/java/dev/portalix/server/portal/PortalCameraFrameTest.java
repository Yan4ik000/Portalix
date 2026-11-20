package dev.portalix.server.portal;

import static dev.portalix.test.PortalFixtures.endpoint;
import static org.junit.jupiter.api.Assertions.*;

import dev.portalix.api.PortalInstance;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalViewLimits;
import dev.portalix.portal.camera.PortalCameraCollision;
import dev.portalix.portal.traversal.PortalCollisionWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class PortalCameraFrameTest {
  @Test
  void distantCameraFramesCannotBootstrapRemoteRegions() {
    var frame = new PortalCameraFrame();
    var path = List.of(link(16, 10000, 1), link(10016, 20000, 2), link(20016, 30000, 3));
    assertFalse(request(frame, path, Vec3.ZERO, Vec3.ZERO, 4));
    assertTrue(frame.frame().isEmpty());
  }

  @Test
  void physicalDistanceAllowsSeveralLargeTeleportsWithinTheBoom() {
    var frame = new PortalCameraFrame();
    var path = List.of(link(1, 10000, 1), link(10001, 20000, 2), link(20001, 30000, 3));
    assertTrue(request(frame, path, Vec3.ZERO, Vec3.ZERO, 4));
    assertEquals(path, frame.frame());
  }

  @Test
  void eachNewRequestMustFitTheWholePathWithoutReplenishingTravel() {
    var frame = new PortalCameraFrame();
    var first = link(3, 10000, 1);
    var second = link(10003, 20000, 2);
    assertTrue(request(frame, List.of(first), Vec3.ZERO, Vec3.ZERO, 4));
    assertFalse(request(frame, List.of(first, second), Vec3.ZERO, Vec3.ZERO, 4));
    assertEquals(List.of(first), frame.frame());
  }

  @Test
  void cameraCanUnwindAnAuthorizedFrameAfterTheBodyMovesAway() {
    var frame = new PortalCameraFrame();
    var first = link(1, 10000, 1);
    var second = link(10001, 20000, 2);
    assertTrue(request(frame, List.of(first, second), Vec3.ZERO, Vec3.ZERO, 4));
    var moved = new Vec3(0, 0, 1000);
    assertTrue(request(frame, List.of(first, second), moved, moved, 4));
    assertTrue(request(frame, List.of(first), moved, moved, 4));
    assertFalse(request(frame, List.of(first, second), moved, moved, 4));
    assertEquals(List.of(first), frame.frame());
  }

  @Test
  void unreachableFrameHasABoundedReturnEvenWhenTheClientKeepsRequestingIt() {
    var camera = new PortalCameraFrame();
    var path = List.of(link(1, 10000, 1));
    assertTrue(request(camera, path, Vec3.ZERO, Vec3.ZERO, 4));
    var moved = new Vec3(0, 0, 128);
    for (int tick = 0; tick < 100; tick++)
      assertTrue(camera.stillReachable(tick, () ->
          PortalCameraFrame.reachable(path, List.of(), Vec3.ZERO, Vec3.ZERO, 4, 0, ignored -> true)));
    boolean expired = false;
    for (int tick = 100; tick < 130; tick++) {
      assertTrue(request(camera, path, moved, moved, 4));
      if (!camera.stillReachable(tick, () ->
          PortalCameraFrame.reachable(path, List.of(), moved, moved, 4, 0, ignored -> true))) {
        assertTrue(tick > 100, "Give the orbit time to return through the aperture");
        expired = true;
        break;
      }
    }
    assertTrue(expired);
  }

  @Test
  void bodyCrossingAuthorizesTheCameraRemainingInItsPreviousSpace() {
    var frame = new PortalCameraFrame();
    var crossing = link(1, 10000, 1);
    frame.crossed(crossing);
    assertEquals(List.of(crossing.inverse()), frame.frame());
    var moved = new Vec3(0, 0, 20000);
    assertTrue(request(frame, List.of(crossing.inverse()), moved, moved, 0));
    frame.crossed(crossing.inverse());
    assertTrue(frame.frame().isEmpty());
  }

  @Test
  void bodyCrossingCancelsTheCameraAlreadyAcrossThatPortal() {
    var frame = new PortalCameraFrame();
    var crossing = link(1, 10000, 1);
    assertTrue(request(frame, List.of(crossing), Vec3.ZERO, Vec3.ZERO, 4));
    frame.crossed(crossing);
    assertTrue(frame.frame().isEmpty());
  }

  @Test
  void aFullCameraFrameCanUnwindAndResetsOnOverflow() {
    var frame = new PortalCameraFrame();
    var crossing = link(1, 10000, 1);
    for (int i = 0; i < PortalViewLimits.CAMERA_DEPTH; i++) frame.crossed(crossing);
    assertEquals(PortalViewLimits.CAMERA_DEPTH, frame.frame().size());
    frame.crossed(crossing.inverse());
    assertEquals(PortalViewLimits.CAMERA_DEPTH - 1, frame.frame().size());
    frame.crossed(crossing);
    frame.crossed(crossing);
    assertTrue(frame.frame().isEmpty());
  }

  @Test
  void interpolatedEyeBesideTheApertureCanEnterFromTheFrontOfItsMovementSegment() {
    var first = link(10000, 0, 1).inverse();
    var second = link(20000, 9999, 2).inverse();
    var from = new Vec3(1.2, 0, .5);
    var to = new Vec3(1.2, 0, -.5);
    var eye = from.lerp(to, .1);
    var world = PortalCollisionWorld.solid(List.of());
    var client = PortalCameraCollision.solve(eye, Vec3.ZERO.subtract(eye), 4,
        List.of(first), (body, move, opening) -> world);
    assertEquals(List.of(first), client.links());
    assertTrue(request(new PortalCameraFrame(), client.links(), from, to, 4));
    assertTrue(request(new PortalCameraFrame(), client.links(), to, from, 4));
    var path = List.of(first, second);
    assertTrue(request(new PortalCameraFrame(), path, from, to, 4));
    assertFalse(request(new PortalCameraFrame(), path, from, to, 1.6));
    assertFalse(request(new PortalCameraFrame(), path, to, to, 4));
  }

  @Test
  void theAuthoritativeMovementSegmentIncludesIntermediateCameraAnchors() {
    var frame = new PortalCameraFrame();
    assertTrue(request(frame, List.of(link(5, 10000, 1)), Vec3.ZERO, new Vec3(0, 0, 10), 0));
  }

  @Test
  void freshCameraFramesRequireTraversalEligibility() {
    var frame = new PortalCameraFrame();
    var path = List.of(link(1, 10000, 1));
    assertFalse(frame.request(path, List.of(), Vec3.ZERO, Vec3.ZERO, 4, 0, ignored -> false));
    assertTrue(frame.frame().isEmpty());
  }

  @Test
  void pointTraversalRejectsBackfaceCrossings() {
    var frame = new PortalCameraFrame();
    var forward = link(1, 10000, 1);
    var backwards = new PortalLink(forward.gun(), forward.generation(),
        new PortalInstance(endpoint(new Vec3(0, 0, 1), new Vec3(0, 0, 1), new Vec3(0, 1, 0)),
            0, 2, null), forward.exit());
    assertFalse(request(frame, List.of(backwards), Vec3.ZERO, Vec3.ZERO, 4));
  }

  @Test
  void obliqueCameraBoomAgreesAtFloorAndWallOpeningEdges() {
    var floor = endpoint(Vec3.ZERO, new Vec3(0, 1, 0), new Vec3(0, 0, 1));
    var wall = endpoint(new Vec3(16_000_000, 10, 1000), new Vec3(0, 0, 1), new Vec3(0, 1, 0));
    var link = new PortalLink(new UUID(0, 1), 1,
        new PortalInstance(floor, 0, 2, null), new PortalInstance(wall, 0, 3, null));
    assertDetachedBoomAgrees(link, new Vec3(.6, 1.8, 0));
    assertDetachedBoomAgrees(link.inverse(), new Vec3(16_000_000.6, 10, 1001.8));
  }

  private static void assertDetachedBoomAgrees(PortalLink link, Vec3 eye) {
    var empty = PortalCollisionWorld.solid(List.of());
    var client = PortalCameraCollision.solve(eye, link.entrance().endpoint().center().subtract(eye), 4,
        List.of(link), (body, movement, opening) -> empty);

    assertEquals(List.of(link), client.links());
    assertTrue(request(new PortalCameraFrame(), client.links(), eye, eye, 4));
  }

  @Test
  void obliqueTravelAtAnApertureEdgeDoesNotRequireAReconstructedCameraHull() {
    var frame = new PortalCameraFrame();
    var up = new Vec3(0, 1, 0);
    var path = List.of(new PortalLink(new UUID(0, 1), 1,
        new PortalInstance(endpoint(new Vec3(143.5, 0, 3), new Vec3(0, 0, -1), up), 0, 2, null),
        new PortalInstance(endpoint(new Vec3(144.5, 0, 10000), new Vec3(0, 0, 1), up), 0, 3, null)));
    var eye = new Vec3(144, 0, 0);
    assertTrue(request(frame, path, eye, eye, 4));
  }

  @Test
  void floorRectangleCornersDoNotCountAsPassableCameraApertures() {
    var frame = new PortalCameraFrame();
    var up = new Vec3(0, 0, 1);
    var floor = new PortalLink(new UUID(0, 1), 1,
        new PortalInstance(endpoint(Vec3.ZERO, new Vec3(0, 1, 0), up), 0, 2, null),
        new PortalInstance(endpoint(new Vec3(10000, 0, 0), new Vec3(0, 0, 1),
            new Vec3(0, 1, 0)), 0, 3, null));
    var corner = new Vec3(.45, .5, .95);
    assertFalse(request(frame, List.of(floor), corner, corner, .51));
  }

  @Test
  void ineligibleExtensionDoesNotReplaceAnAuthorizedCameraFrame() {
    var frame = new PortalCameraFrame();
    var first = link(1, 10000, 1);
    assertTrue(request(frame, List.of(first), Vec3.ZERO, Vec3.ZERO, 4));
    assertFalse(frame.request(List.of(first, link(10001, 20000, 2)), List.of(), Vec3.ZERO, Vec3.ZERO,
        4, 0, ignored -> false));
    assertEquals(List.of(first), frame.frame());
    var moved = new Vec3(0, 0, 1000);
    assertTrue(frame.request(List.of(first), List.of(), moved, moved, 0, 0, ignored -> false));
  }

  @Test
  void cameraBoomCanExtendTheAuthoritativeEyePathFromItsExit() {
    var frame = new PortalCameraFrame();
    var first = link(0, 10000, 1);
    var second = link(10002, 20000, 2);
    var bodyEye = new Vec3(0, 0, .3);
    var eyePath = List.of(first);
    var camera = List.of(first, second);
    assertFalse(frame.request(camera, eyePath, bodyEye, bodyEye, 1, 5, link -> true));
    assertFalse(frame.request(camera, eyePath, bodyEye, bodyEye, 2, 5, link -> false));
    assertTrue(frame.request(camera, eyePath, bodyEye, bodyEye, 2, 5, second::equals));
    assertEquals(camera, frame.frame());
  }

  @Test
  void authoritativeEyeFramesNeedNoThirdPersonCameraHull() {
    var frame = new PortalCameraFrame();
    var path = List.of(link(1, 10000, 1));
    assertFalse(frame.request(path, List.of(), Vec3.ZERO, Vec3.ZERO, 0, 0, ignored -> false));
    assertTrue(frame.request(path, path, Vec3.ZERO, Vec3.ZERO, 0, 0, ignored -> false));
    assertEquals(path, frame.frame());
  }

  @Test
  void redundantInverseLinksAreRejectedAndAcceptedFramesOwnTheirLists() {
    var frame = new PortalCameraFrame();
    var first = link(1, 10000, 1);
    assertFalse(request(frame, List.of(first, first.inverse()), Vec3.ZERO, Vec3.ZERO, 4));
    var path = new ArrayList<>(List.of(first));
    assertTrue(request(frame, path, Vec3.ZERO, Vec3.ZERO, 4));
    path.clear();
    assertEquals(List.of(first), frame.frame());
    assertThrows(UnsupportedOperationException.class, frame.frame()::clear);
    frame.clear();
    assertTrue(frame.frame().isEmpty());
  }

  private static PortalLink link(double entranceZ, double exitZ, long identity) {
    var up = new Vec3(0, 1, 0);
    return new PortalLink(new UUID(0, identity), 1,
        new PortalInstance(endpoint(new Vec3(0, 0, entranceZ), new Vec3(0, 0, -1), up),
            0, identity * 2, null),
        new PortalInstance(endpoint(new Vec3(0, 0, exitZ), new Vec3(0, 0, 1), up),
            0, identity * 2 + 1, null));
  }

  private static boolean request(PortalCameraFrame frame, List<PortalLink> path,
      Vec3 from, Vec3 to, double reach) {
    return frame.request(path, List.of(), from, to, reach, 0, ignored -> true);
  }
}
