package dev.portalix.portal.traversal;

import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class WorldCollisionShapes {
  private WorldCollisionShapes() {}

  public static List<AABB> boxes(Entity entity, AABB query) {
    var boxes = new ArrayList<AABB>();
    var level = entity.level();
    var expanded = query.inflate(1e-7);
    var blocks = level.getBlockCollisions(entity, expanded);
    var entities = level.getEntityCollisions(entity, expanded);
    var border = level.getWorldBorder();
    var borderShape =
        border.isInsideCloseToBorder(entity, expanded) ? border.getCollisionShape() : null;
    Shapes.DoubleLineConsumer collect =
        (x0, y0, z0, x1, y1, z1) -> boxes.add(new AABB(x0, y0, z0, x1, y1, z1));
    for (var shape : blocks) shape.forAllBoxes(collect);
    for (var shape : entities) shape.forAllBoxes(collect);
    if (borderShape != null) borderShape.forAllBoxes(collect);
    return boxes;
  }

  public static Iterable<VoxelShape> shapes(Entity entity, AABB query) {
    var level = entity.level();
    var border = level.getWorldBorder();
    return Iterables.concat(
        level.getBlockCollisions(entity, query),
        level.getEntityCollisions(entity, query),
        border.isInsideCloseToBorder(entity, query)
            ? List.of(border.getCollisionShape())
            : List.of());
  }
}
