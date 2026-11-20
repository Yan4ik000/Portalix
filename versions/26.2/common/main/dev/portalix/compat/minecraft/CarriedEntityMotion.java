package dev.portalix.compat.minecraft;

import dev.portalix.mixin.carry.ItemEntityAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

public final class CarriedEntityMotion {
  private CarriedEntityMotion() {}

  public static void grab(Entity entity) {
    entity.setDeltaMovement(Vec3.ZERO);
    entity.hurtMarked = true;
    entity.fallDistance = 0;
    if (entity instanceof Mob mob) mob.getNavigation().stop();
    if (entity instanceof LivingEntity living) {
      living.setJumping(false);
      living.xxa = living.yya = living.zza = 0;
    }
  }

  public static void hold(Entity entity, float yaw) {
    entity.setDeltaMovement(Vec3.ZERO);
    entity.fallDistance = 0;
    entity.setYRot(yaw);
    entity.setYHeadRot(yaw);
    entity.setYBodyRot(yaw);
  }

  public static void release(Entity entity, int minimumPickupDelay) {
    entity.fallDistance = 0;
    entity.setOnGround(false);
    entity.verticalCollision = false;
    entity.verticalCollisionBelow = false;
    entity.horizontalCollision = false;
    entity.minorHorizontalCollision = false;
    if (entity instanceof Mob mob) mob.getNavigation().stop();
    if (entity instanceof ItemEntity item) {
      int delay = ((ItemEntityAccessor) item).portalix$pickupDelay();
      item.setPickUpDelay(delay == 32767 ? 32767 : Math.max(minimumPickupDelay, delay));
    }
  }
}
