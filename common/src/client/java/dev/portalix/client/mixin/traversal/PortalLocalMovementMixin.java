package dev.portalix.client.mixin.traversal;

import dev.portalix.client.portal.ClientPortalMovement;
import dev.portalix.portal.traversal.PortalEntityMovement;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class PortalLocalMovementMixin {
  @Inject(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V",
              shift = At.Shift.AFTER))
  private void portalix$finishMovement(CallbackInfo ci) {
    ClientPortalMovement.flushMovement();
  }

  @Inject(method = "move", at = @At("HEAD"), cancellable = true)
  private void portalix$segments(MoverType type, Vec3 displacement, CallbackInfo ci) {

    if (PortalEntityMovement.move((LocalPlayer) (Object) this, type, displacement)) ci.cancel();
  }
}
