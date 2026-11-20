package dev.portalix.portal.query;

import static dev.portalix.test.PortalFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.geometry.ConvexPolyhedron;
import dev.portalix.portal.geometry.PortalAperture;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PortalRaycastTest {
  static Stream<PortalEndpoint> portals() {
    return orientations().stream().map(portal -> at(portal, new Vec3(1, 10, -3)));
  }

  private static HitResult miss(Vec3 from, Vec3 to) {
    return BlockHitResult.miss(to, Direction.NORTH, BlockPos.containing(to));
  }

  @Test
  void exposedHitboxNarrowerThanTheCornerInsetStillHasAnUnobstructedSightline() {
    var up = new Vec3(0, 1, 0);
    var entrance = endpoint(new Vec3(20, 0, 1), new Vec3(0, 0, -1), up);
    var exit = endpoint(new Vec3(0, 0, 1), new Vec3(0, 0, 1), up);
    var path = List.of(link(entrance, exit));
    var eye = Vec3.ZERO;
    var target = new AABB(-.3, .05, 4, .3, .669, 4.6);
    var slab = new AABB(-.5, 0, 3, .5, .5, 4);
    var occlusion = new PortalInteractionGeometry.Occlusion(ConvexPolyhedron.box(slab).shadowFrom(eye));
    PortalInteractionGeometry.Sightline sightline = point -> slab.clip(eye, point).isPresent() ? occlusion : null;

    var exposed = PortalInteractionGeometry.closestFrom(eye, target, path, 4.1, sightline).orElseThrow();
    assertTrue(exposed.y >= target.minY && exposed.y <= target.maxY);
    assertTrue(slab.clip(eye, exposed).isEmpty());
    assertTrue(PortalAperture.containsPassage(exit, eye.lerp(exposed, 1 / exposed.z)));
    assertTrue(PortalInteractionGeometry.closestFrom(eye, target, path, 4.02, sightline).isEmpty());
    assertTrue(PortalInteractionGeometry.closestFrom(eye, target.setMaxY(.6), path, 4.1, sightline).isEmpty());
  }

  @Test
  void aVoxelFaceAtTheEyeOnlyCoversRaysDirectedIntoTheVoxel() {
    var voxel = ConvexPolyhedron.box(new AABB(0, -.5, -.5, 1, .5, .5));
    var shadow = voxel.shadowFrom(Vec3.ZERO);
    assertTrue(shadow.stream().allMatch(plane -> plane.normal().dot(new Vec3(2, 1, 0)) <= plane.offset()));
    assertTrue(shadow.stream().anyMatch(plane -> plane.normal().dot(new Vec3(-2, 1, 0)) > plane.offset()));
  }

  @Test
  void rayAndReachRespectTheNarrowerEndInBothDirections() {
    var wall = endpoint(new Vec3(0, 4, 0), new Vec3(0, 0, 1), new Vec3(0, 1, 0));
    var floor = endpoint(new Vec3(24, 4, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1));
    var pair = link(wall, floor);
    for (var passage : List.of(pair, pair.inverse())) {
      var entrance = passage.entrance().endpoint();
      for (double offset : new double[] {0, .38}) {
        var point = entrance.center().add(entrance.right().scale(offset)).add(entrance.up().scale(.55));
        var eye = point.add(entrance.normal());
        var movement = entrance.normal().scale(-2);
        var target = passage.transform().point(eye.add(movement));
        var ray = PortalRaycast.cast(eye, movement, List.of(passage), ignored -> true, PortalRaycastTest::miss);
        assertEquals(offset == 0, !ray.links().isEmpty());
        assertEquals(offset == 0, PortalInteractionGeometry.closest(
            eye, AABB.ofSize(target, .01, .01, .01), List.of(passage)).isPresent());
      }
    }
  }

  @ParameterizedTest
  @MethodSource("portals")
  void rayTraversalConservesRangeAndHonorsReadinessAndOcclusion(PortalEndpoint entrance) {
    Vec3 start = entrance.center().add(entrance.normal().scale(2));
    Vec3 movement = entrance.normal().scale(-5);
    for (PortalEndpoint orientation : orientations()) {
      var exit = at(orientation, new Vec3(20, 12, 30));
      var link = link(entrance, exit);
      var ray = PortalRaycast.cast(start, movement, List.of(link, link.inverse()), ignored -> true, PortalRaycastTest::miss);
      assertEquals(List.of(link), ray.links());
      assertEquals(2, ray.segments().size());
      assertEquals(5, ray.distance(), 1e-8);
      assertVector(exit.center().add(exit.normal().scale(3)), ray.hit().getLocation(), 1e-8);
      assertEquals(ray.distance(), ray.segments().stream().mapToDouble(segment -> segment.from().distanceTo(segment.to())).sum(), 1e-8);
      var exitSegment = ray.segments().getLast();
      assertVector(exit.normal().scale(3), exitSegment.to().subtract(exitSegment.from()), 1e-8);

      var blocked = PortalRaycast.cast(start, movement, List.of(link), ignored -> false, PortalRaycastTest::miss);
      assertTrue(blocked.links().isEmpty());
      assertVector(start.add(movement), blocked.hit().getLocation(), 1e-12);

      Vec3 obstruction = start.add(movement.scale(.2));
      var occluded = PortalRaycast.cast(start, movement, List.of(link), ignored -> true,
          (from, to) -> new BlockHitResult(obstruction, Direction.NORTH, BlockPos.containing(obstruction), false));
      assertTrue(occluded.links().isEmpty());
      assertEquals(1, occluded.distance(), 1e-8);
      assertEquals(HitResult.Type.BLOCK, occluded.hit().getType());
    }
  }
}
