package dev.portalix.client.portal.audio;

import static dev.portalix.test.ClientPortalFixtures.endpoint;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.portalix.portal.audio.PortalSoundPropagation.Observer;
import dev.portalix.portal.geometry.PortalAperture;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class PortalEffectObserversTest {
  @Test
  void samplingPartitionsReachableBlocksWithOneStableFrontSideOwner() {
    var primary = new BlockPos(-17, 5, -33);
    var local = new Observer(List.of(), new Vec3(-19.8, 6.2, -34.6), null);
    var overlap = new ArrayList<Observer>();
    overlap.add(local);
    for (var direction : Direction.values()) {
      var normal = Vec3.atLowerCornerOf(direction.getUnitVec3i());
      var center = Vec3.atCenterOf(primary).add(normal.scale(3));
      overlap.add(new Observer(List.of(), center.add(normal.scale(2)), endpoint(center, direction)));
    }
    // Coincident centers must still use input order, including the primary observer first.
    overlap.add(new Observer(List.of(), local.position(), endpoint(Vec3.atCenterOf(primary), Direction.WEST)));
    var islands = List.of(local,
        new Observer(List.of(), new Vec3(1000.3, 8.8, -2000.2), endpoint(new Vec3(1000, 5, -2000), Direction.UP)),
        new Observer(List.of(), new Vec3(-1000.4, -8.8, 2000.6), endpoint(new Vec3(-1000, -5, 2000), Direction.DOWN)));
    for (var observers : List.of(List.of(local), List.copyOf(overlap), islands)) {
      // Animation uses nextInt(r)-nextInt(r); weather uses nextInt(2r+1)-r.
      verify(observers, primary, 31, 31, 31, 31);
      verify(observers, primary, 31, 31, 15, 15);
      verify(observers, primary, 4, 10, 4, 10);
    }
  }

  private static void verify(List<Observer> observers, BlockPos primary,
      int maxHorizontal, int maxVertical, int horizontal, int vertical) {
    var sampling = new PortalEffectObservers.Sampling(observers, primary, maxHorizontal, maxVertical);
    var samples = new HashSet<BlockPos>();
    for (var observer : observers) {
      var origin = observer.exit() == null ? primary : BlockPos.containing(observer.position());
      for (int x : offsets(horizontal))
        for (int y : offsets(vertical))
          for (int z : offsets(horizontal)) samples.add(origin.offset(x, y, z));
    }
    for (var sample : samples) {
      var point = Vec3.atCenterOf(sample);
      Observer owner = null;
      double nearest = Double.POSITIVE_INFINITY;
      for (var candidate : observers) {
        var origin = candidate.exit() == null ? primary : BlockPos.containing(candidate.position());
        if (Math.abs(sample.getX() - origin.getX()) > horizontal
            || Math.abs(sample.getY() - origin.getY()) > vertical
            || Math.abs(sample.getZ() - origin.getZ()) > horizontal) continue;
        var exit = candidate.exit();
        if (exit != null && point.subtract(exit.center()).dot(exit.normal()) < -PortalAperture.PLANE_EPSILON)
          continue;
        double distance = candidate.position().distanceToSqr(point);
        if (distance < nearest) {
          owner = candidate;
          nearest = distance;
        }
      }
      int owners = 0;
      for (var observer : observers) {
        boolean owns = sampling.owns(observer, sample, horizontal, vertical);
        assertEquals(owner == observer, owns, "sampling ownership at " + sample);
        if (owns) owners++;
      }
      assertEquals(owner == null ? 0 : 1, owners);
    }
  }

  private static int[] offsets(int radius) {
    return new int[] {-radius - 1, -radius, -radius + 1, 0, radius - 1, radius, radius + 1};
  }
}
