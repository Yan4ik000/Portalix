package dev.portalix.portal.geometry;

import static dev.portalix.test.PortalFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.portalix.api.PortalEndpoint;
import java.util.Random;
import java.util.stream.Stream;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PortalGeometryTest {
  static Stream<PortalEndpoint> portals() {
    return orientations().stream();
  }

  @ParameterizedTest
  @MethodSource("portals")
  void endpointBasisIsOrthonormalAndRightHanded(PortalEndpoint portal) {
    assertEquals(1, portal.normal().length(), 1e-12);
    assertEquals(1, portal.up().length(), 1e-12);
    assertEquals(1, portal.right().length(), 1e-12);
    assertEquals(0, portal.normal().dot(portal.up()), 1e-12);
    assertVector(portal.normal(), portal.right().cross(portal.up()), 1e-12);
    var scaled = endpoint(portal.center(), portal.normal().scale(1e300), portal.up().scale(1e-300));
    assertVector(portal.normal(), scaled.normal(), 1e-12);
    assertVector(portal.up(), scaled.up(), 1e-12);
  }

  @Test
  void invalidGeometryIsRejectedAtItsBoundary() {
    Vec3 normal = new Vec3(0, 0, 1), up = new Vec3(0, 1, 0);
    for (Vec3 invalid : new Vec3[] {Vec3.ZERO, new Vec3(Double.NaN, 0, 0), new Vec3(0, Double.POSITIVE_INFINITY, 0)}) {
      assertThrows(IllegalArgumentException.class, () -> endpoint(Vec3.ZERO, invalid, up));
      assertThrows(IllegalArgumentException.class, () -> endpoint(Vec3.ZERO, normal, invalid));
    }
    assertThrows(IllegalArgumentException.class, () -> endpoint(Vec3.ZERO, normal, normal));
    assertThrows(IllegalArgumentException.class, () -> endpoint(new Vec3(Double.NaN, 0, 0), normal, up));
    assertThrows(IllegalArgumentException.class, () -> new PortalEndpoint(DIMENSION, Vec3.ZERO, normal, up, 2, 2));
  }

  @ParameterizedTest
  @MethodSource("portals")
  void transformsPreserveGeometryAndInvertAcrossEveryExit(PortalEndpoint entrance) {
    var random = new Random(0x504f5254414cL);
    for (PortalEndpoint exit : orientations()) {
      var transform = new PortalTransform(entrance, exit);
      assertVector(exit.center(), transform.point(entrance.center()), 1e-8);
      assertVector(exit.normal(), transform.vector(entrance.normal().scale(-1)), 1e-12);
      assertVector(exit.up(), transform.vector(entrance.up()), 1e-12);
      for (int i = 0; i < 3; i++) {
        Vec3 vector = new Vec3(random.nextDouble(-10, 10), random.nextDouble(-10, 10), random.nextDouble(-10, 10));
        Vec3 point = entrance.center().add(vector);
        assertVector(point, transform.inversePoint(transform.point(point)), 5e-8);
        assertVector(vector, transform.inverse().vector(transform.vector(vector)), 1e-11);
        assertEquals(vector.lengthSqr(), transform.vector(vector).lengthSqr(), 1e-10);
        var rotated = transform.rotation().transform(new Vector3f((float) vector.x, (float) vector.y, (float) vector.z));
        assertVector(transform.vector(vector), new Vec3(rotated.x, rotated.y, rotated.z), 5e-6);
      }
      var box = new AABB(entrance.center().add(-.3, -.7, -.2), entrance.center().add(.4, .8, .5));
      var transformed = transform.bounds(box);
      var polyhedron = ConvexPolyhedron.box(box).transform(transform);
      assertVector(transformed.getCenter(), polyhedron.bounds().getCenter(), 2e-8);
      assertEquals(transformed.getXsize(), polyhedron.bounds().getXsize(), 2e-8);
      assertEquals(transformed.getYsize(), polyhedron.bounds().getYsize(), 2e-8);
      assertEquals(transformed.getZsize(), polyhedron.bounds().getZsize(), 2e-8);
      for (Vec3 vertex : polyhedron.vertices()) {
        assertTrue(polyhedron.contains(vertex.lerp(transformed.getCenter(), 1e-6)));
      }
    }
  }

  @ParameterizedTest
  @MethodSource("portals")
  void crossingIsDirectionalAndPreservesUnconsumedMovement(PortalEndpoint portal) {
    Vec3 start = portal.center().add(portal.normal().scale(2));
    Vec3 movement = portal.normal().scale(-5).add(portal.right().scale(.25));
    var crossing = PortalAperture.crossing(portal, start, movement).orElseThrow();
    assertEquals(.4, crossing.fraction(), 1e-9);
    assertEquals(0, PortalAperture.signedDistance(portal, crossing.point()), 1e-8);
    assertVector(start.add(movement), crossing.point().add(crossing.remaining()), 1e-8);
    assertTrue(PortalAperture.crossing(portal, start, movement.scale(-1)).isEmpty());
    assertTrue(PortalAperture.crossing(portal, start, portal.right()).isEmpty());
    assertTrue(PortalAperture.crossing(portal, start.add(portal.right().scale(2)), movement).isEmpty());
    assertTrue(PortalAperture.crossing(portal, portal.center().subtract(portal.normal().scale(3)), movement).isEmpty());
  }

  @ParameterizedTest
  @MethodSource("portals")
  void shortestApertureRouteIsSymmetricAndNoLongerThanAnySampledRoute(PortalEndpoint portal) {
    Vec3 from = portal.center().add(portal.normal().scale(3)).add(portal.right().scale(2));
    Vec3 to = portal.center().subtract(portal.normal().scale(2)).subtract(portal.up().scale(3));
    Vec3 shortest = PortalAperture.shortestPathPoint(portal, from, to);
    assertVector(shortest, PortalAperture.shortestPathPoint(portal, to, from), 1e-8);
    assertTrue(PortalAperture.contains(portal, shortest));
    assertEquals(0, PortalAperture.signedDistance(portal, shortest), 1e-8);
    double length = from.distanceTo(shortest) + shortest.distanceTo(to);
    for (int x = -5; x <= 5; x += 5) {
      for (int y = -10; y <= 10; y += 10) {
        Vec3 sample = portal.center().add(portal.right().scale(x / 10.0)).add(portal.up().scale(y / 10.0));
        assertTrue(length <= from.distanceTo(sample) + sample.distanceTo(to) + 1e-7);
      }
    }
  }

  @Test
  void convexClippingAndContinuousCollisionKeepTheirPhysicalMeaning() {
    var moving = ConvexPolyhedron.box(new AABB(-.5, -.5, -.5, .5, .5, .5));
    var obstacle = ConvexPolyhedron.box(new AABB(2, -1, -1, 3, 1, 1));
    var hit = moving.sweep(new Vec3(4, 0, 0), obstacle).orElseThrow();
    assertEquals(.375, hit.fraction(), 1e-12);
    assertVector(new Vec3(-1, 0, 0), hit.normal(), 1e-12);
    assertFalse(hit.startedInside());
    assertTrue(moving.sweep(new Vec3(-4, 0, 0), obstacle).isEmpty());
    assertTrue(moving.sweep(new Vec3(0, 4, 0), obstacle).isEmpty());
    assertTrue(moving.intersects(moving));
    assertFalse(moving.intersects(obstacle));
    var clipped = moving.clip(ConvexPolyhedron.Plane.through(new Vec3(1, 0, 0), Vec3.ZERO));
    assertFalse(clipped.isEmpty());
    assertEquals(0, clipped.bounds().maxX, 1e-12);
    assertVector(new Vec3(0, .5, -.5), clipped.closestPoint(new Vec3(2, 3, -4)).orElseThrow(), 1e-12);
    assertTrue(clipped.clip(ConvexPolyhedron.Plane.through(new Vec3(-1, 0, 0), new Vec3(1, 0, 0))).isEmpty());
  }

  @ParameterizedTest
  @MethodSource("portals")
  void collisionAndClippingRemainEquivalentNearTheWorldBorder(PortalEndpoint orientation) {
    var entrance = at(orientation, new Vec3(29_999_984, -60, -29_999_984));
    var exit = endpoint(new Vec3(-29_999_980, 300, 29_999_980), new Vec3(1, 2, 3), new Vec3(-2, 1, 0));
    var transform = new PortalTransform(entrance, exit);
    Vec3 center = entrance.center();
    var moving = ConvexPolyhedron.box(new AABB(center.add(-.5, -.5, -.5), center.add(.5, .5, .5)));
    var obstacle = ConvexPolyhedron.box(new AABB(center.add(2, -1, -1), center.add(3, 1, 1)));
    Vec3 movement = new Vec3(4, 0, 0);
    var original = moving.sweep(movement, obstacle).orElseThrow();
    var transferred = moving.transform(transform).sweep(transform.vector(movement), obstacle.transform(transform)).orElseThrow();
    assertEquals(original.fraction(), transferred.fraction(), 1e-7);
    assertVector(transform.vector(original.normal()), transferred.normal(), 1e-7);
    assertEquals(original.startedInside(), transferred.startedInside());
    var clipped = moving.clip(ConvexPolyhedron.Plane.through(new Vec3(1, 0, 0), center));
    var transferredClip = clipped.transform(transform);
    assertFalse(transferredClip.isEmpty());
    Vec3 inner = center.add(-.25, .1, -.1), outer = center.add(.25, .1, -.1);
    assertTrue(transferredClip.contains(transform.point(inner)));
    assertFalse(transferredClip.contains(transform.point(outer)));
    assertVector(transform.point(center.add(0, .1, -.1)), transferredClip.closestPoint(transform.point(outer)).orElseThrow(), 2e-7);
  }
}
