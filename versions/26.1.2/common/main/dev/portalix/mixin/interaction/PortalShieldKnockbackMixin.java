package dev.portalix.mixin.interaction;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.portal.query.PortalAttackDamageSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
abstract class PortalShieldKnockbackMixin {
  @Unique private static final ScopedValue<ShieldBlock> portalix$shieldBlock = ScopedValue.newInstance();

  @WrapOperation(
      method = "applyItemBlocking",
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/world/entity/LivingEntity;blockUsingItem(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"))
  private void portalix$carryShieldFrame(LivingEntity defender, ServerLevel level,
      LivingEntity attacker, Operation<Void> original, @Local(argsOnly = true) DamageSource source) {
    ScopedValue.where(portalix$shieldBlock, new ShieldBlock(attacker, defender, source))
        .run(() -> original.call(defender, level, attacker));
  }

  @WrapOperation(
      method = "blockedByItem",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"))
  private void portalix$shieldDirection(
      LivingEntity defender, double power, double x, double z, Operation<Void> original) {
    if (portalix$shieldBlock.isBound()) {
      var block = portalix$shieldBlock.get();
      if (block.attacker() == (Object) this && block.defender() == defender
          && block.source() instanceof PortalAttackDamageSource portal) {
        x = defender.getX() - portal.getSourcePosition().x;
        z = defender.getZ() - portal.getSourcePosition().z;
      }
    }
    original.call(defender, power, x, z);
  }

  private record ShieldBlock(LivingEntity attacker, LivingEntity defender, DamageSource source) {}
}
