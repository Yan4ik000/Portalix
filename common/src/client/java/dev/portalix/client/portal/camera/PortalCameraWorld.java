package dev.portalix.client.portal.camera;

import dev.portalix.portal.PortalLink;
import dev.portalix.portal.traversal.PortalCollisionWorld;
import dev.portalix.portal.traversal.WorldCollisionShapes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class PortalCameraWorld {
  private PortalCameraWorld() {}

  static PortalCollisionWorld query(Entity entity, AABB body, Vec3 movement, PortalLink opening, boolean ready) {
    var local = visualBoxes(entity, body, movement);
    var border = entity.level().getWorldBorder();
    var localBorder = WorldCollisionShapes.borderBoxes(border, body.expandTowards(movement));
    if (opening == null) {
      local.addAll(localBorder);
      return PortalCollisionWorld.solid(local);
    }
    if (!ready) return PortalCollisionWorld.material(opening, local, List.of(), localBorder);
    var remoteBody = opening.transform().bounds(body);
    var remoteMovement = opening.transform().vector(movement);
    var remote = visualBoxes(entity, remoteBody, remoteMovement);
    remote.addAll(WorldCollisionShapes.borderBoxes(border, remoteBody.expandTowards(remoteMovement)));
    return PortalCollisionWorld.material(opening, local, remote, localBorder);
  }

  private static List<AABB> visualBoxes(Entity entity, AABB body, Vec3 movement) {
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
    return boxes;
  }
}
