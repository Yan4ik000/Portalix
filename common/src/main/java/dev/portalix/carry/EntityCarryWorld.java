package dev.portalix.carry;

import dev.portalix.portal.traversal.PortalEntityCollisions;
import dev.portalix.portal.traversal.WorldCollisionShapes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public record EntityCarryWorld(Entity entity) implements CarryCollisionWorld {
  @Override
  public AABB bounds() {
    return entity.getBoundingBox();
  }

  @Override
  public List<AABB> obstacles(AABB query, int maxShapes, int maxBoxes) {
    List<AABB> result = new ArrayList<>();
    int shapes = 0;
    for (VoxelShape shape : WorldCollisionShapes.shapes(entity, query)) {
      if (++shapes > maxShapes || !add(result, shape, maxBoxes)) return null;
    }
    return result;
  }

  private static boolean add(List<AABB> result, VoxelShape shape, int maxBoxes) {
    for (AABB box : shape.toAabbs()) {
      if (result.size() >= maxBoxes) return false;
      result.add(box);
    }
    return true;
  }

  @Override
  public CarryCollisionSweep.Result sweep(Vec3 center, Vec3 desired) {
    return sweep(center, desired, true);
  }

  public CarryCollisionSweep.Result sweepWithinSpace(Vec3 center, Vec3 desired) {
    return sweep(center, desired, false);
  }

  private CarryCollisionSweep.Result sweep(Vec3 center, Vec3 desired, boolean crossAtEnd) {
    Vec3 delta = desired.subtract(center);
    AABB bounds = bounds();
    AABB hull = bounds.move(center.subtract(bounds.getCenter()));
    return PortalHullSweep.move(entity, hull, delta, crossAtEnd);
  }

  @Override
  public boolean crossesPortal(Vec3 center, Vec3 desired) {
    AABB bounds = bounds();
    return PortalEntityCollisions.intersectingPortal(
            entity, bounds.move(center.subtract(bounds.getCenter())), desired.subtract(center))
        != null;
  }
}
