package dev.portalix.portal.physics;

import static dev.portalix.test.PortalFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.geometry.PortalTransform;
import java.util.stream.Stream;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PortalPhysicsTest {
  static Stream<PortalEndpoint> portals() {
    return orientations().stream().map(p -> at(p, new Vec3(2, 10, -3)));
  }

  @ParameterizedTest
  @MethodSource("portals")
  void ballisticsCrossingsLandOnThePlaneWithinTheCurrentTick(PortalEndpoint portal) {
    for (double gravity : new double[] {0, .03, .08, .2}) {
      for (double startTime : new double[] {0, .2, .75}) {
        double duration = (1 - startTime) * .6;
        Vec3 velocity = portal.normal().scale(-2).add(portal.right().scale(.15));
        Vec3 displacement = PortalBallistics.displacement(velocity, gravity, startTime, duration);
        Vec3 start = portal.center().subtract(displacement);
        double actual = PortalBallistics.crossing(portal, start, velocity, gravity, startTime).orElseThrow();
        assertEquals(duration, actual, 1e-10);
        Vec3 crossing = start.add(PortalBallistics.displacement(velocity, gravity, startTime, actual));
        assertEquals(0, PortalAperture.signedDistance(portal, crossing), 1e-10);
        assertTrue(PortalBallistics.velocity(velocity, gravity, startTime + actual).dot(portal.normal()) < 0);
        Vec3 first = PortalBallistics.displacement(velocity, gravity, startTime, actual / 2);
        Vec3 second = PortalBallistics.displacement(velocity, gravity, startTime + actual / 2, actual / 2);
        assertVector(displacement, first.add(second), 1e-12);
      }
    }
    assertTrue(PortalBallistics.crossing(portal, portal.center().add(portal.normal().scale(4)), portal.normal().scale(-1), 0, 0).isEmpty());
    assertTrue(PortalBallistics.crossing(portal, portal.center().subtract(portal.normal()), portal.normal().scale(-1), 0, 0).isEmpty());
  }

  @ParameterizedTest
  @MethodSource("portals")
  void exitSpeedPolicyPreservesTangentialPlayerMotionAndEntityDirection(PortalEndpoint portal) {
    Vec3 normal = portal.normal();
    Vec3 tangent = portal.up().scale(.7).add(portal.right().scale(.3));
    for (double incoming : new double[] {-2, 0, .2, 2}) {
      Vec3 velocity = normal.scale(incoming).add(tangent);
      Vec3 player = PortalExitVelocity.forPlayer(velocity, normal, 1);
      assertEquals(Math.max(incoming, 1), player.dot(normal), 1e-12);
      assertVector(tangent, player.subtract(normal.scale(player.dot(normal))), 1e-12);
      Vec3 entity = PortalExitVelocity.forEntity(velocity, normal, 1);
      assertEquals(Math.max(velocity.length(), 1), entity.length(), 1e-12);
      assertVector(velocity.normalize(), entity.normalize(), 1e-12);
    }
    assertVector(normal, PortalExitVelocity.forEntity(Vec3.ZERO, normal, 1), 1e-12);
    var transform = new PortalTransform(portal, portal);
    if (Math.abs(portal.normal().y) < .5) {
      Vec3 incoming = portal.normal().scale(-.1);
      assertVector(transform.vector(incoming), PortalExitVelocity.adjustedVelocity(transform, incoming, portal.center(), 1, .08), 1e-12);
    }
  }

  @Test
  void floorLaunchRetainsItsRequiredRiseUnderDifferentGravityAndDrag() {
    double requiredRise = 300.0 * 300 / (2 * 600) * dev.portalix.resource.SourceUnits.SOURCE_UNIT_BLOCKS;
    for (double gravity : new double[] {.01, .04, .08, .16, .3}) {
      for (double drag : new double[] {.8, .98, 1}) {
        double velocity = PortalExitVelocity.playerFloorExitSpeed(gravity, drag);
        double height = 0, peak = 0;
        for (int tick = 0; tick < 1000 && velocity > 0; tick++) {
          velocity = (velocity - gravity) * drag;
          height += velocity;
          peak = Math.max(peak, height);
        }
        assertTrue(peak >= requiredRise - 1e-10, "rise under gravity=" + gravity + ", drag=" + drag);
      }
    }
  }
}
