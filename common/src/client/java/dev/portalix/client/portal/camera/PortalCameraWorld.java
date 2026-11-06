package dev.portalix.client.portal.camera;

import dev.portalix.portal.PortalLink;
import dev.portalix.portal.traversal.PortalCollisionWorld;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class PortalCameraWorld {
  private PortalCameraWorld() {}

  static PortalCollisionWorld query(Entity entity, AABB body, Vec3 movement, PortalLink opening) {
    var local = shapes(entity, body, movement);
    return opening == null
        ? PortalCollisionWorld.solid(local)
        : PortalCollisionWorld.material(
            opening,
            local,
            shapes(entity, opening.transform().bounds(body), opening.transform().vector(movement)));
  }

  private static List<AABB> shapes(Entity entity, AABB body, Vec3 movement) {
    var level = entity.level();
    var context =
        new ClipContext(Vec3.ZERO, Vec3.ZERO, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity);
    var boxes = new ArrayList<AABB>();
    var bounds = body.expandTowards(movement).inflate(1e-7);
    // Visual shapes can extend beyond their block, such as the top of a fence.
    BlockGetter.forEachBlockIntersectedBetween(
        body.getCenter(),
        body.getCenter().add(movement),
        body.move(movement).inflate(1),
        (pos, iteration) -> {
          var shape = context.getBlockShape(level.getBlockState(pos), level, pos);
          shape.forAllBoxes(
              (x0, y0, z0, x1, y1, z1) -> {
                var box =
                    new AABB(
                        x0 + pos.getX(), y0 + pos.getY(), z0 + pos.getZ(),
                        x1 + pos.getX(), y1 + pos.getY(), z1 + pos.getZ());
                if (box.intersects(bounds)) boxes.add(box);
              });
          return true;
        });
    var border = level.getWorldBorder();
    if (border.isInsideCloseToBorder(entity, bounds))
      border.getCollisionShape().forAllBoxes(
          (x0, y0, z0, x1, y1, z1) -> boxes.add(new AABB(x0, y0, z0, x1, y1, z1)));
    return boxes;
  }
}
