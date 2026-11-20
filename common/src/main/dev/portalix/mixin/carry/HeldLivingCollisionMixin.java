package dev.portalix.mixin.carry;

import dev.portalix.carry.CarryBindings;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class HeldLivingCollisionMixin {
  @Inject(
      method = {"push(Lnet/minecraft/world/entity/Entity;)V", "doPush"},
      at = @At("HEAD"),
      cancellable = true)
  private void portalix$push(Entity other, CallbackInfo result) {
    if (CarryBindings.ignores((Entity) (Object) this, other)) {
      result.cancel();
    }
  }
}
