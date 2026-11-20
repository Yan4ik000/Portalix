package dev.portalix.portal.traversal;

import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class WorldCollisionShapes {
  private WorldCollisionShapes() {}

  public static List<AABB> boxes(Entity entity, AABB query) {
    return boxes(entity, query, true);
  }

  public static List<AABB> solidBoxes(Entity entity, AABB query) {
    return boxes(entity, query, false);
  }

  private static List<AABB> boxes(Entity entity, AABB query, boolean includeBorder) {
    var boxes = new ArrayList<AABB>();
    var level = entity.level();
    var expanded = query.inflate(1e-7);
    var blocks = level.getBlockCollisions(entity, expanded);
    var entities = level.getEntityCollisions(entity, expanded);
    Shapes.DoubleLineConsumer collect =
        (x0, y0, z0, x1, y1, z1) -> boxes.add(new AABB(x0, y0, z0, x1, y1, z1));
    for (var shape : blocks) shape.forAllBoxes(collect);
    for (var shape : entities) shape.forAllBoxes(collect);
    if (includeBorder) boxes.addAll(borderBoxes(level.getWorldBorder(), query));
    return boxes;
  }

  public static Function<AABB, List<AABB>> borderBoxes(Entity entity) {
    var border = entity.level().getWorldBorder();
    return query -> borderBoxes(border, query);
  }

  public static List<AABB> borderBoxes(WorldBorder border, AABB query) {
    // Match the whole-block collision faces used by WorldBorder.
    double minX = Math.floor(border.getMinX()), maxX = Math.ceil(border.getMaxX());
    double minZ = Math.floor(border.getMinZ()), maxZ = Math.ceil(border.getMaxZ());
    double west = minX - query.minX, east = query.maxX - maxX;
    double north = minZ - query.minZ, south = query.maxZ - maxZ;
    double depth = Math.max(Math.max(west, east), Math.max(north, south));
    if (depth < -1e-7) return List.of();
    // Keep artificial faces farther away than the real border even if the query starts outside.
    double span = Math.max(query.getXsize(), Math.max(query.getYsize(), query.getZsize()));
    var bounds = query.inflate(span + Math.max(0, depth) + 2e-7);
    var result = new ArrayList<AABB>(4);
    if (west >= -1e-7)
      result.add(new AABB(bounds.minX, bounds.minY, bounds.minZ, minX, bounds.maxY, bounds.maxZ));
    if (east >= -1e-7)
      result.add(new AABB(maxX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ));
    if (north >= -1e-7)
      result.add(new AABB(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, minZ));
    if (south >= -1e-7)
      result.add(new AABB(bounds.minX, bounds.minY, maxZ, bounds.maxX, bounds.maxY, bounds.maxZ));
    return result;
  }

  public static Iterable<VoxelShape> solidShapes(Entity entity, AABB query) {
    var level = entity.level();
    return Iterables.concat(level.getBlockCollisions(entity, query), level.getEntityCollisions(entity, query));
  }
}
