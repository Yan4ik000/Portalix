package dev.portalix.mixin.interaction;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.portal.query.PortalAttackDamageSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
abstract class PortalShieldKnockbackMixin {
  @WrapOperation(
      method = "blockedByItem",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/LivingEntity;knockback(DDDLnet/minecraft/world/damagesource/DamageSource;F)V"))
  private void portalix$shieldDirection(
      LivingEntity defender, double power, double x, double z, DamageSource source, float damage,
      Operation<Void> original) {
    if (source instanceof PortalAttackDamageSource portal) {
      x = defender.getX() - portal.getSourcePosition().x;
      z = defender.getZ() - portal.getSourcePosition().z;
    }
    original.call(defender, power, x, z, source, damage);
  }
}
