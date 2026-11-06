package dev.portalix.portal.query;

import static dev.portalix.test.PortalFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.portalix.api.PortalEndpoint;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PortalRaycastTest {
  static Stream<PortalEndpoint> portals() {
    return orientations().stream().map(portal -> at(portal, new Vec3(1, 10, -3)));
  }

  private static HitResult miss(Vec3 from, Vec3 to) {
    return BlockHitResult.miss(to, Direction.NORTH, BlockPos.containing(to));
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
      assertVector(link.transform().vector(movement), ray.vector(movement), 1e-12);

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
