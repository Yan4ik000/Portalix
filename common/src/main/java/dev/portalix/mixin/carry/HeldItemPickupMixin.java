package dev.portalix.mixin.carry;

import dev.portalix.carry.CarryBindings;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEntity.class)
public abstract class HeldItemPickupMixin {
  @Inject(method = "hasPickUpDelay", at = @At("HEAD"), cancellable = true)
  private void portalix$pickupDelay(CallbackInfoReturnable<Boolean> result) {
    if (CarryBindings.holderId((ItemEntity) (Object) this) >= 0) result.setReturnValue(true);
  }

  @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
  private void portalix$pickup(Player player, CallbackInfo result) {
    if (CarryBindings.holderId((ItemEntity) (Object) this) >= 0) result.cancel();
  }

  @Inject(method = "isMergable", at = @At("HEAD"), cancellable = true)
  private void portalix$merge(CallbackInfoReturnable<Boolean> result) {
    if (CarryBindings.holderId((ItemEntity) (Object) this) >= 0) result.setReturnValue(false);
  }

  @Inject(method = "tryToMerge", at = @At("HEAD"), cancellable = true)
  private void portalix$mergePair(ItemEntity other, CallbackInfo result) {
    if (CarryBindings.holderId((ItemEntity) (Object) this) >= 0
        || CarryBindings.holderId(other) >= 0) result.cancel();
  }
}
