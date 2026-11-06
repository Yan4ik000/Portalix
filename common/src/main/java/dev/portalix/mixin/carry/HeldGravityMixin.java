package dev.portalix.mixin.carry;

import dev.portalix.carry.CarryBindings;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class HeldGravityMixin {
  @Inject(method = "getGravity", at = @At("HEAD"), cancellable = true)
  private void portalix$gravity(CallbackInfoReturnable<Double> result) {
    if (CarryBindings.holderId((Entity) (Object) this) >= 0) result.setReturnValue(0.0);
  }
}
