package dev.portalix.mixin.interaction;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.portal.query.PortalInteractionPath;
import dev.portalix.portal.traversal.PortalPassagePose;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
abstract class PortalInteractionRangeMixin {
  @WrapOperation(
      method = {
        "isWithinEntityInteractionRange(Lnet/minecraft/world/entity/Entity;D)Z",
        "doSweepAttack(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/damagesource/DamageSource;F)V"
      },
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"),
      require = 2,
      allow = 2)
  private AABB portalix$targetBounds(Entity entity, Operation<AABB> original) {
    return PortalPassagePose.interactionBounds(entity, original.call(entity));
  }

  @Inject(method = "isWithinBlockInteractionRange", at = @At("RETURN"), cancellable = true)
  private void portalix$block(
      BlockPos pos, double buffer, CallbackInfoReturnable<Boolean> callback) {
    if (callback.getReturnValueZ()) return;
    var player = (Player) (Object) this;
    double range = player.blockInteractionRange() + buffer;
    if (PortalInteractionPath.distance(player, new AABB(pos), range) < range)
      callback.setReturnValue(true);
  }

  @Inject(
      method = "isWithinEntityInteractionRange(Lnet/minecraft/world/phys/AABB;D)Z",
      at = @At("RETURN"),
      cancellable = true)
  private void portalix$entity(
      AABB target, double buffer, CallbackInfoReturnable<Boolean> callback) {
    if (callback.getReturnValueZ()) return;
    var player = (Player) (Object) this;
    double range = player.entityInteractionRange() + buffer;
    if (PortalInteractionPath.distance(player, target, range) < range)
      callback.setReturnValue(true);
  }

  @Inject(method = "isWithinAttackRange", at = @At("RETURN"), cancellable = true)
  private void portalix$attack(
      ItemStack weapon, AABB target, double buffer, CallbackInfoReturnable<Boolean> callback) {
    if (callback.getReturnValueZ()) return;
    var player = (Player) (Object) this;
    var range = player.getAttackRangeWith(weapon);
    double maximum = range.effectiveMaxRange(player) + range.hitboxMargin() + buffer;
    double minimum = range.effectiveMinRange(player) - range.hitboxMargin() - buffer;
    double distance = PortalInteractionPath.distance(player, target, maximum);
    if (distance >= minimum && distance <= maximum) callback.setReturnValue(true);
  }
}
