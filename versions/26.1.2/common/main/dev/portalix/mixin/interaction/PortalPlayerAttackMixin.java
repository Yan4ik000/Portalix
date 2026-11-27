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
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
abstract class PortalPlayerAttackMixin {
  @Unique private static final ScopedValue<AttackKnockback> portalix$knockback = ScopedValue.newInstance();

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
      method = {"attack", "stabAttack"},
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/world/entity/player/Player;causeExtraKnockback(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/phys/Vec3;)V"),
      require = 2,
      allow = 2)
  private void portalix$carryAttackFrame(Player player, Entity target, float strength,
      Vec3 movement, Operation<Void> original, @Local DamageSource source) {
    ScopedValue.where(portalix$knockback, new AttackKnockback(player, target, source))
        .run(() -> original.call(player, target, strength, movement));
  }

  @WrapOperation(
      method = "causeExtraKnockback",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getYRot()F"))
  private float portalix$attackYaw(
      Player player, Operation<Float> original, @Local(argsOnly = true) Entity target) {
    if (portalix$knockback.isBound()) {
      var attack = portalix$knockback.get();
      if (attack.player() == player && attack.target() == target
          && attack.source() instanceof PortalAttackDamageSource portal) return portal.yaw();
    }
    return original.call(player);
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
                  "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"),
      require = 1,
      allow = 1)
  private void portalix$sweepDirection(
      LivingEntity target, double power, double x, double z, Operation<Void> original,
      @Local(argsOnly = true) DamageSource source) {
    if (source instanceof PortalAttackDamageSource portal) {
      x = Mth.sin(portal.yaw() * Mth.DEG_TO_RAD);
      z = -Mth.cos(portal.yaw() * Mth.DEG_TO_RAD);
    }
    original.call(target, power, x, z);
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

  private record AttackKnockback(Player player, Entity target, DamageSource source) {}
}
