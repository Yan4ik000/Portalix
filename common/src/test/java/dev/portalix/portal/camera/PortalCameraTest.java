package dev.portalix.portal.camera;

import static dev.portalix.test.PortalFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.api.PortalInstance;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalViewLimits;
import dev.portalix.portal.geometry.PortalTransform;
import dev.portalix.portal.traversal.PortalCollisionWorld;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PortalCameraTest {
  static Stream<PortalEndpoint> portals() {
    return orientations().stream();
  }

  @ParameterizedTest
  @MethodSource("portals")
  void teleportKeepsTheVisibleDirectionContinuousAndEventuallyRestoresUpright(PortalEndpoint entrance) {
    for (PortalEndpoint exit : orientations()) {
      var transform = new PortalTransform(entrance, exit);
      for (boolean preserve : new boolean[] {false, true}) {
        for (float pitch : new float[] {-90, 0, 90}) {
          var camera = new PortalCameraReorientation(preserve);
          camera.resetView(37.5f, pitch);
          var before = camera.orientation();
          var expected = new Quaternionf(before).premul(transform.rotation());
          camera.teleport(new Matrix3f().set(transform.rotation()));
          assertOrientation(expected, camera.orientation());
          for (int frame = 0; frame < 1200 && camera.active(); frame++) camera.update(1 / 60f);
          assertFalse(camera.active(), "camera did not settle");
          var settled = camera.orientation();
          assertTrue(Float.isFinite(settled.x) && Float.isFinite(settled.y) && Float.isFinite(settled.z) && Float.isFinite(settled.w));
          assertEquals(1, settled.lengthSquared(), 1e-5);
          var right = settled.transform(new Vector3f(1, 0, 0));
          assertEquals(0, right.y, 2e-5, "settled camera retains roll");
          var up = settled.transform(new Vector3f(0, 1, 0));
          assertTrue(up.y >= -1e-5, "settled camera is upside down");
        }
      }
    }
  }

  @Test
  void cameraStopsAtTheLastEncodableFrameAndCanReturnThroughItsExit() {
    var up = new Vec3(0, 1, 0);
    var a = endpoint(Vec3.ZERO, new Vec3(0, 0, 1), up);
    var b = endpoint(new Vec3(0, 0, 1), new Vec3(0, 0, -1), up);
    var link = new PortalLink(new UUID(0, 1), 1,
        new PortalInstance(a, 0, 1, null), new PortalInstance(b, 0, 2, null));
    var links = List.of(link, link.inverse());
    var empty = PortalCollisionWorld.solid(List.of());
    PortalCameraCollision.Query world = (body, motion, opening) -> empty;
    var outward = PortalCameraCollision.solve(new Vec3(0, 0, .5), new Vec3(0, 0, -1), 80, links, world);
    assertEquals(PortalViewLimits.CAMERA_DEPTH, outward.links().size());
    assertTrue(outward.position().z > 0 && outward.position().z < .001);
    var returning = PortalCameraCollision.solve(outward.position(), new Vec3(0, 0, 1), 1.5, links, world, outward.links());
    assertEquals(List.of(link.inverse()), returning.links());
  }

  @Test
  void lookAnglesRoundTripIncludingBothPitchPolesAndMouseLimits() {
    for (float yaw : new float[] {-180, 0, 37.5f}) {
      for (float pitch : new float[] {-90, 0, 90}) {
        var rotation = PortalLookAngles.rotation(yaw, pitch);
        var angles = PortalLookAngles.from(rotation);
        var forward = rotation.transform(new Vector3f(0, 0, -1));
        var restored = PortalLookAngles.rotation(angles.yaw(), angles.pitch()).transform(new Vector3f(0, 0, -1));
        assertVector(new Vec3(forward.x, forward.y, forward.z), new Vec3(restored.x, restored.y, restored.z), 2e-5);
        var camera = new PortalCameraReorientation();
        camera.resetView(yaw, pitch);
        for (int step = 0; step < 20; step++) camera.mouse(123.5, 999);
        assertTrue(Math.abs(camera.angles().pitch()) <= 90);
        assertEquals(1, camera.orientation().lengthSquared(), 1e-5);
        camera.mouse(-777, -999);
        assertTrue(Math.abs(camera.angles().pitch()) <= 90);
        assertEquals(1, camera.orientation().lengthSquared(), 1e-5);
      }
    }
  }

  private static void assertOrientation(Quaternionf expected, Quaternionf actual) {
    for (Vector3f axis : new Vector3f[] {new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)}) {
      var a = expected.transform(new Vector3f(axis));
      var b = actual.transform(new Vector3f(axis));
      assertVector(new Vec3(a.x, a.y, a.z), new Vec3(b.x, b.y, b.z), 2e-5);
    }
  }
}
