package dev.portalix.client.mixin.traversal;

import dev.portalix.client.portal.RemotePortalInterpolation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InterpolationHandler.class)
abstract class PortalInterpolationMixin implements RemotePortalInterpolation.Access {
  @Shadow @Final private Entity entity;
  @Shadow private int interpolationSteps;
  @Unique private RemotePortalInterpolation portalix$path;

  @Override
  public int portalix$length() {
    return interpolationSteps;
  }

  @Override
  public RemotePortalInterpolation portalix$interpolation() {
    if (portalix$path == null) portalix$path = new RemotePortalInterpolation(entity);
    return portalix$path;
  }

  @Inject(method = "interpolateTo", at = @At("HEAD"), cancellable = true)
  private void portalix$target(Vec3 position, float yaw, float pitch, CallbackInfo ci) {
    if (portalix$path != null && portalix$path.active()) {
      portalix$path.target(position, yaw, pitch, interpolationSteps);
      ci.cancel();
    }
  }

  @Inject(method = "position", at = @At("HEAD"), cancellable = true)
  private void portalix$position(CallbackInfoReturnable<Vec3> cir) {
    if (portalix$path != null && portalix$path.active())
      cir.setReturnValue(portalix$path.position());
  }

  @Inject(method = "yRot", at = @At("HEAD"), cancellable = true)
  private void portalix$yaw(CallbackInfoReturnable<Float> cir) {
    if (portalix$path != null && portalix$path.active()) cir.setReturnValue(portalix$path.yaw());
  }

  @Inject(method = "xRot", at = @At("HEAD"), cancellable = true)
  private void portalix$pitch(CallbackInfoReturnable<Float> cir) {
    if (portalix$path != null && portalix$path.active())
      cir.setReturnValue(portalix$path.pitch());
  }

  @Inject(method = "hasActiveInterpolation", at = @At("HEAD"), cancellable = true)
  private void portalix$active(CallbackInfoReturnable<Boolean> cir) {
    if (portalix$path != null && portalix$path.active()) cir.setReturnValue(true);
  }

  @Inject(method = "interpolate", at = @At("HEAD"), cancellable = true)
  private void portalix$tick(CallbackInfo ci) {
    if (portalix$path == null || !portalix$path.active()) return;
    portalix$path.tick();
    ci.cancel();
    if (!portalix$path.active()) ((InterpolationHandler) (Object) this).cancel();
  }

  @Inject(method = "cancel", at = @At("HEAD"))
  private void portalix$cancel(CallbackInfo ci) {
    if (portalix$path != null) portalix$path.cancel();
  }
}
