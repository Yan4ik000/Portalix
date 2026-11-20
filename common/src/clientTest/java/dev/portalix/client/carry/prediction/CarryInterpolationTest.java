package dev.portalix.client.carry.prediction;

import static org.junit.jupiter.api.Assertions.*;

import dev.portalix.carry.CarryCollisionSweep;
import dev.portalix.carry.CarryCollisionWorld;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class CarryInterpolationTest {
  @Test
  void smoothsOnlyCorrectionsReachableByTheWholeHeldBody() {
    var wall = new AABB(1, -2, -2, 1.2, 2, 2);
    CarryCollisionWorld world = new CarryCollisionWorld() {
      public AABB bounds() { return AABB.ofSize(Vec3.ZERO, .5, .5, .5); }
      public List<AABB> obstacles(AABB query, int maxShapes, int maxBoxes) { return List.of(wall); }
      public CarryCollisionSweep.Result sweep(Vec3 from, Vec3 to) {
        return CarryCollisionSweep.move(bounds().move(from), to.subtract(from), List.of(wall));
      }
    };
    var interpolation = new CarryInterpolation(Vec3.ZERO, Vec3.ZERO);
    var clear = new Vec3(.5, 0, 0);
    assertEquals(clear, interpolation.offsetPosition(world, clear));
    assertNull(interpolation.offsetPosition(world, new Vec3(.9, 0, 0)));
    assertNull(interpolation.offsetPosition(world, new Vec3(2, 0, 0)));
    assertEquals(Vec3.ZERO, interpolation.position());
  }
}
