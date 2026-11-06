package dev.portalix.mixin.carry;

import dev.portalix.carry.CarryBindings;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class HeldCollisionMixin {
  @Inject(
      method = {"canCollideWith", "canBeCollidedWith"},
      at = @At("HEAD"),
      cancellable = true)
  private void portalix$contact(Entity other, CallbackInfoReturnable<Boolean> result) {
    if (CarryBindings.ignores((Entity) (Object) this, other)) {
      result.setReturnValue(false);
    }
  }

  @Inject(
      method = "push(Lnet/minecraft/world/entity/Entity;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void portalix$push(Entity other, CallbackInfo result) {
    if (CarryBindings.ignores((Entity) (Object) this, other)) {
      result.cancel();
    }
  }
}
