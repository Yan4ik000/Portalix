package dev.portalix.compat.minecraft;

import dev.portalix.mixin.traversal.LivingEntityAccessor;
import dev.portalix.mixin.traversal.PlayerAccessor;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.camera.PortalCameraCorrections;
import dev.portalix.portal.traversal.PortalEntityMovement.Crossing;
import dev.portalix.portal.traversal.PortalPassagePose;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.phys.Vec3;

public final class EntityMotion {
  private EntityMotion() {}

  public static double stepGravity(Entity entity, MoverType mover, Vec3 velocity, Vec3 movement) {
    return entity instanceof Player player
            && !player.onGround()
            && !player.isFallFlying()
            && !player.getAbilities().flying
            && !player.isInWater()
            && !player.isInLava()
            && !player.hasEffect(MobEffects.LEVITATION)
            && (mover == MoverType.SELF || mover == MoverType.PLAYER)
            && velocity.distanceToSqr(movement) < 1e-12
        ? ((LivingEntityAccessor) player).portalix$effectiveGravity()
        : 0;
  }

  public static Vec3 backOffFromEdge(Player player, Vec3 movement, MoverType mover) {
    return ((PlayerAccessor) player).portalix$backOffFromEdge(movement, mover);
  }

  public static float airDrag(Player player) {
    return player.shouldDiscardFriction() ? 1 : ((LivingEntityAccessor) player).portalix$airDrag();
  }

  public static void prepareTracking(Entity entity) {
    entity.syncPosition = true;
    entity.needsSync = true;
    if (entity instanceof AbstractMinecart minecart
        && minecart.getBehavior() instanceof NewMinecartBehavior behavior)
      behavior.lerpSteps.clear();
  }

  public static Vec3 rebaseHistory(Entity entity, PortalLink link, Vec3 before, Vec3 after,
      Vec3 tickFeet, double tickStart, double fraction) {
    Vec3 renderFeet = entity.oldPosition();
    Vec3 previous = fraction == 0 ? after : PortalCameraCorrections.transformEarlier(
        tickFeet, tickStart, tickStart + fraction, link, before, after);
    Vec3 rendered = fraction == 0 ? after : PortalCameraCorrections.transformEarlier(
        renderFeet, 0, fraction, link, before, after);
    history(entity, previous, rendered);
    return renderFeet;
  }

  public static void receiveCrossing(Entity entity, Crossing crossing) {
    PortalPassagePose.set(entity, crossing.poseScale());
    entity.setPos(crossing.afterFeet());
    Vec3 previous = crossing.moveFraction() == 0 ? crossing.afterFeet()
        : PortalCameraCorrections.transformEarlier(crossing.previousFeet(), 0,
            crossing.moveFraction(), crossing.link(), crossing.beforeFeet(), crossing.afterFeet());
    history(entity, previous, previous);
  }

  public static void receivedRotation(Entity entity, float yaw, float pitch, float head, float body) {
    entity.setYRot(yaw);
    entity.yRotO = yaw;
    entity.setXRot(pitch);
    entity.xRotO = pitch;
    if (entity instanceof LivingEntity living) {
      living.setYHeadRot(head);
      living.yHeadRotO = head;
      living.yBodyRot = body;
      living.yBodyRotO = body;
    }
  }

  public static void snapPosition(Entity entity, Vec3 position) {
    cancelInterpolation(entity);
    entity.setPos(position);
    history(entity, position, position);
  }

  public static void snap(Entity entity, Vec3 position, float yaw, float pitch, Vec3 velocity) {
    cancelInterpolation(entity);
    entity.snapTo(position, yaw, pitch);
    entity.setDeltaMovement(velocity);
    entity.setOldPosAndRot();
  }

  private static void cancelInterpolation(Entity entity) {
    var interpolation = entity.getInterpolation();
    if (interpolation != null) interpolation.cancel();
  }

  private static void history(Entity entity, Vec3 tickFeet, Vec3 renderFeet) {
    entity.xo = tickFeet.x;
    entity.yo = tickFeet.y;
    entity.zo = tickFeet.z;
    entity.xOld = renderFeet.x;
    entity.yOld = renderFeet.y;
    entity.zOld = renderFeet.z;
  }
}
