package dev.portalix.carry;

import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public interface CarryCollisionWorld {
  AABB bounds();

  List<AABB> obstacles(AABB query, int maxShapes, int maxBoxes);

  CarryCollisionSweep.Result sweep(Vec3 center, Vec3 desired);

  default boolean crossesPortal(Vec3 center, Vec3 desired) {
    return false;
  }
}
