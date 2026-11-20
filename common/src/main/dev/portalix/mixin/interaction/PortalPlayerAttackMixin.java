package dev.portalix.mixin.interaction;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.portal.query.PortalAttackDamageSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
abstract class PortalPlayerAttackMixin {
  @WrapOperation(
      method = {"attack", "stabAttack"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/player/Player;createAttackSource(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/damagesource/DamageSource;"),
      require = 2,
      allow = 2)
  private DamageSource portalix$attackFrame(
      Player player, ItemStack weapon, Operation<DamageSource> original,
      @Local(argsOnly = true) Entity target) {
    return PortalAttackDamageSource.throughPortal(player, target, weapon, original.call(player, weapon));
  }

  @WrapOperation(
      method = "causeExtraKnockback",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getYRot()F"))
  private float portalix$attackYaw(
      Player player, Operation<Float> original, @Local(argsOnly = true) DamageSource source) {
    return source instanceof PortalAttackDamageSource portal ? portal.yaw() : original.call(player);
  }

  @WrapOperation(
      method = {
        "doSweepAttack(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/damagesource/DamageSource;F)V",
        "doSweepAttack(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/damagesource/DamageSource;FLnet/minecraft/world/phys/AABB;)V"
      },
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/LivingEntity;knockback(DDDLnet/minecraft/world/damagesource/DamageSource;F)V"),
      require = 1,
      allow = 1)
  private void portalix$sweepDirection(
      LivingEntity target, double power, double x, double z, DamageSource source, float damage,
      Operation<Void> original) {
    if (source instanceof PortalAttackDamageSource portal) {
      x = Mth.sin(portal.yaw() * Mth.DEG_TO_RAD);
      z = -Mth.cos(portal.yaw() * Mth.DEG_TO_RAD);
    }
    original.call(target, power, x, z, source, damage);
  }

  @WrapOperation(
      method = {
        "doSweepAttack(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/damagesource/DamageSource;F)V",
        "doSweepAttack(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/damagesource/DamageSource;FLnet/minecraft/world/phys/AABB;)V"
      },
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/player/Player;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"),
      require = 1,
      allow = 1)
  private double portalix$sweepDistance(
      Player player, Entity target, Operation<Double> original,
      @Local(argsOnly = true) DamageSource source) {
    return source instanceof PortalAttackDamageSource portal
        ? portal.distanceToSqr(target)
        : original.call(player, target);
  }
}
