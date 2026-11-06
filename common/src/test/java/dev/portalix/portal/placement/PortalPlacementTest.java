package dev.portalix.portal.placement;

import static dev.portalix.test.PortalFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.portalix.api.PortalEndpoint;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PortalPlacementTest {
  static Stream<Direction> faces() {
    return Stream.of(Direction.values());
  }

  @ParameterizedTest
  @MethodSource("faces")
  void placementFitsSupportedFacesAndSeparatesExistingPortals(Direction face) {
    Vec3 normal = PortalSurface.vector(face);
    Vec3 center = new Vec3(.5, .5, .5).add(normal.scale(.5));
    var desired = endpoint(center, normal, PortalSurface.up(face, 0));
    var supported = PortalPlacementSolver.findPlacement(desired, List.of(), block -> true).orElseThrow();
    assertVector(desired.center(), supported.center(), 1e-12);
    assertEquals(desired.normal(), supported.normal());
    assertFalse(PortalSurface.placementFootprint(supported).blocks().isEmpty());

    var next = PortalPlacementSolver.findPlacement(desired, List.of(supported), block -> true).orElseThrow();
    assertFalse(PortalSurface.overlapsPortal(supported, next));
    assertTrue(next.center().distanceToSqr(desired.center()) <= PortalSurface.MAX_BUMP_DISTANCE_SQUARED + 1e-8);
    assertTrue(PortalSurface.coplanar(desired, next));
    assertTrue(PortalPlacementSolver.findPlacement(desired, List.of(), block -> false).isEmpty());
  }

  @ParameterizedTest
  @MethodSource("faces")
  void placementNeverReturnsUnsupportedOrOverlappingFootprints(Direction face) {
    Vec3 normal = PortalSurface.vector(face);
    Vec3 up = PortalSurface.up(face, 0);
    Vec3 center = new Vec3(.5, .5, .5).add(normal.scale(.5));
    var base = endpoint(center, normal, up);
    var occupied = List.of(at(base, center.add(base.right().scale(2))), at(base, center.subtract(base.right().scale(2))));
    int accepted = 0;
    for (double x : new double[] {-1.4, -.4, 0, .4, 1.4}) {
      for (double y : new double[] {-.8, 0, .8}) {
        var desired = at(base, center.add(base.right().scale(x)).add(up.scale(y)));
        var result = PortalPlacementSolver.findPlacement(desired, occupied, block -> {
          Vec3 relative = Vec3.atCenterOf(block).subtract(center);
          return Math.abs(relative.dot(base.right())) < 2.5 && Math.abs(relative.dot(up)) < 2.5;
        });
        if (result.isPresent()) accepted++;
        result.ifPresent(portal -> {
          assertTrue(portal.center().distanceToSqr(desired.center()) <= PortalSurface.MAX_BUMP_DISTANCE_SQUARED + 1e-8);
          assertEquals(0, portal.center().subtract(desired.center()).dot(normal), 1e-10);
          for (PortalEndpoint other : occupied) assertFalse(PortalSurface.overlapsPortal(portal, other));
          for (BlockPos block : PortalSurface.placementFootprint(portal).blocks()) {
            Vec3 relative = Vec3.atCenterOf(block).subtract(center);
            assertTrue(Math.abs(relative.dot(base.right())) < 2.5 && Math.abs(relative.dot(up)) < 2.5);
          }
        });
      }
    }
    assertTrue(accepted > 0, "The supported fixture produced no placements");
  }
}
