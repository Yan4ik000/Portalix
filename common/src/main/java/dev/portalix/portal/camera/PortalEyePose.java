package dev.portalix.portal.camera;

import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.query.PortalRaycast;
import dev.portalix.portal.traversal.EntityPortalState;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class PortalEyePose {
  private PortalEyePose() {}

  public static Vec3 renderedFeet(Entity entity, float partial) {
    return EntityPortalState.of(entity)
        .cameraHistory()
        .renderPosition(entity.oldPosition(), entity.position(), entity.tickCount, partial);
  }

  public static Vec3 interpolatedFeet(Entity entity, float partial) {
    return EntityPortalState.of(entity)
        .cameraHistory()
        .position(
            new Vec3(entity.xo, entity.yo, entity.zo),
            entity.position(),
            entity.tickCount,
            partial);
  }

  public static Vec3 interpolatedCenter(Entity entity, float partial) {
    Vec3 center = entity.getBoundingBox().getCenter();
    Vec3 previous =
        new Vec3(entity.xo, entity.yo, entity.zo).add(center.subtract(entity.position()));
    return EntityPortalState.of(entity)
        .cameraHistory()
        .center(previous, center, entity.tickCount, partial);
  }

  public static Pose sample(Entity entity) {
    return sample(entity, 1);
  }

  public static Pose sample(Entity entity, float partialTick) {
    Vec3 feet = interpolatedFeet(entity, partialTick);
    Vec3 center = interpolatedCenter(entity, partialTick);
    var state = EntityPortalState.of(entity);
    Vec3 eye =
        feet.add(0, entity.getEyeHeight(), 0)
            .add(state.eyeCorrection(entity.tickCount - 1 + partialTick));
    Vec3 forward =
        entity instanceof Player && !entity.isPassenger()
            ? Vec3.directionFromRotation(entity.getXRot(partialTick), entity.getYRot(partialTick))
            : entity.getViewVector(partialTick);
    var path = state.cameraHistory().rewind(entity.tickCount - 1 + partialTick);
    for (var link : path) {
      center = link.transform().point(center);
      eye = link.transform().point(eye);
      forward = link.transform().vector(forward);
    }
    var eyePath = eyePath(entity, center, eye);
    for (var link : eyePath) {
      eye = link.transform().point(eye);
      forward = link.transform().vector(forward);
    }
    return new Pose(eye, forward, eyePath);
  }

  public static List<PortalLink> eyePath(Entity entity, Vec3 bodyCenter, Vec3 eye) {
    if (PortalTopology.links(entity.level()).isEmpty()) return List.of();
    return PortalRaycast.cast(entity, bodyCenter, eye.subtract(bodyCenter), PortalEyePose::miss).links();
  }

  private static BlockHitResult miss(Vec3 from, Vec3 to) {
    return BlockHitResult.miss(to, Direction.NORTH, BlockPos.containing(to));
  }

  public record Pose(Vec3 position, Vec3 forward, List<PortalLink> eyePath) {}
}
