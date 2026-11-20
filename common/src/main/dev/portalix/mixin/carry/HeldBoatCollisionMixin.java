package dev.portalix.mixin.carry;

import dev.portalix.carry.CarryBindings;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBoat.class)
public abstract class HeldBoatCollisionMixin {
  @Inject(method = "canVehicleCollide", at = @At("HEAD"), cancellable = true)
  private static void portalix$vehicleContact(
      Entity vehicle, Entity other, CallbackInfoReturnable<Boolean> result) {
    if (CarryBindings.ignores(vehicle, other)) result.setReturnValue(false);
  }

  @Inject(method = "canBeCollidedWith", at = @At("HEAD"), cancellable = true)
  private void portalix$contact(Entity other, CallbackInfoReturnable<Boolean> result) {
    if (CarryBindings.ignores((Entity) (Object) this, other)) result.setReturnValue(false);
  }
}
