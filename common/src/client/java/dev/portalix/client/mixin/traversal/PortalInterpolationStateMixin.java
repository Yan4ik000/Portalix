package dev.portalix.client.mixin.traversal;

import dev.portalix.client.portal.RemotePortalInterpolation;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.SteppedInterpolationHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LinearInterpolationHandler.class, SteppedInterpolationHandler.class})
abstract class PortalInterpolationStateMixin {
  @Inject(method = "hasActiveInterpolation", at = @At("HEAD"), cancellable = true)
  private void portalix$active(CallbackInfoReturnable<Boolean> cir) {
    var interpolation = ((RemotePortalInterpolation.Access) this).portalix$activeInterpolation();
    if (interpolation != null && interpolation.driving()) cir.setReturnValue(true);
  }

  @Inject(method = "cancel", at = @At("HEAD"))
  private void portalix$cancel(CallbackInfo ci) {
    var interpolation = ((RemotePortalInterpolation.Access) this).portalix$existingInterpolation();
    if (interpolation != null) interpolation.cancel();
  }
}
