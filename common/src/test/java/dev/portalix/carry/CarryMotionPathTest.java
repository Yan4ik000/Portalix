package dev.portalix.carry;

import static dev.portalix.test.PortalFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class CarryMotionPathTest {
  @Test
  void interpolationMeasuresTravelWithoutCountingThePortalSeam() {
    for (var entrance : orientations()) {
      for (var exit : orientations()) {
        var link = link(entrance, exit);
        Vec3 seam = entrance.center();
        Vec3 start = seam.add(entrance.normal().scale(2));
        Vec3 transferred = link.transform().point(seam);
        Vec3 finish = transferred.add(exit.normal().scale(2));
        var path = CarryMotionPath.ofNodes(List.of(
            new CarryMotionPath.Node(start, null),
            new CarryMotionPath.Node(seam, null),
            new CarryMotionPath.Node(transferred, link),
            new CarryMotionPath.Node(finish, null)));
        assertVector(start, path.center(0), 1e-8);
        assertVector(start.lerp(seam, .5), path.center(.25f), 1e-8);
        assertVector(transferred.lerp(finish, .5), path.center(.75f), 1e-8);
        assertVector(finish, path.center(1), 1e-8);
        assertTrue(path.crossings(.49f).isEmpty());
        assertEquals(List.of(link), path.crossings(.51f));
        assertEquals(List.of(link), path.crossings(1));
        assertVector(link.transform().vector(new Vec3(1, 2, 3)), path.vector(new Vec3(1, 2, 3)), 1e-10);
        assertVector(link.transform().point(start), path.point(start), 1e-8);
        assertEquals(4, path.movement(.25f).length(), 1e-8);
        assertEquals(4, path.movement(.75f).length(), 1e-8);
      }
    }
  }

  @Test
  void stationaryAndRepeatedPointsRemainFiniteAndInvalidSeamsAreRejected() {
    Vec3 point = new Vec3(1, 2, 3);
    var stationary = CarryMotionPath.stationary(point);
    var repeated = new CarryMotionPath(List.of(point, point, point));
    for (float partial : new float[] {-1, 0, .5f, 1, 2}) {
      assertVector(point, stationary.center(partial), 0);
      assertVector(Vec3.ZERO, stationary.movement(partial), 0);
      assertVector(point, repeated.center(partial), 0);
      assertVector(Vec3.ZERO, repeated.movement(partial), 0);
    }
    assertThrows(IllegalArgumentException.class, () -> new CarryMotionPath(List.of()));
    var portal = orientations().getFirst();
    var link = link(portal, at(portal, new Vec3(100, 10, 10)));
    assertThrows(IllegalArgumentException.class, () -> CarryMotionPath.ofNodes(List.of(new CarryMotionPath.Node(point, link))));
    assertThrows(IllegalArgumentException.class, () -> CarryMotionPath.ofNodes(List.of(new CarryMotionPath.Node(point, null), new CarryMotionPath.Node(point, link))));
  }
}
