package dev.portalix.mixin.carry;

import dev.portalix.carry.CarryBindings;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class HeldMobAiMixin {
  @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
  private void portalix$ai(CallbackInfo result) {
    if (CarryBindings.holderId((Mob) (Object) this) >= 0) result.cancel();
  }
}
