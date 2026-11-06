package dev.portalix.client.mixin.carry;

import dev.portalix.carry.CarryBindings;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class HeldLivingAnimationMixin {
  @Inject(
      method =
          "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
      at = @At("TAIL"))
  private void portalix$carriedPose(
      LivingEntity entity, LivingEntityRenderState state, float partial, CallbackInfo ci) {
    if (CarryBindings.holderId(entity) >= 0) {
      state.walkAnimationSpeed = 0.0F;
      state.walkAnimationPos = 0.0F;
    }
  }
}
