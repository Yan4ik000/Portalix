package dev.portalix.client.mixin.traversal;

import dev.portalix.client.portal.RemotePortalInterpolation;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.world.entity.AbstractInterpolationHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionPath;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractInterpolationHandler.class)
abstract class PortalInterpolationMixin implements RemotePortalInterpolation.Access {
  @Shadow @Final protected Entity entity;
  @Shadow protected int interpolationSteps;
  @Unique private @Nullable RemotePortalInterpolation portalix$path;

  @Override
  public int portalix$length() {
    return interpolationSteps;
  }

  @Override
  public RemotePortalInterpolation portalix$interpolation() {
    if (portalix$path == null) portalix$path = new RemotePortalInterpolation(entity);
    return portalix$path;
  }

  @Override
  public @Nullable RemotePortalInterpolation portalix$existingInterpolation() {
    return portalix$path;
  }

  @Inject(method = "interpolateTo(Lnet/minecraft/world/entity/PositionPath;FFZ)Z",
      at = @At("HEAD"), cancellable = true)
  private void portalix$updateTarget(
      @Nullable PositionPath path, float yaw, float pitch, boolean hasRotation,
      CallbackInfoReturnable<Boolean> cir) {
    var interpolation = portalix$activeInterpolation();
    if (interpolation == null) return;
    interpolation.update(path, yaw, pitch, hasRotation, interpolationSteps);
    cir.setReturnValue(true);
  }

  @Inject(method = "target", at = @At("HEAD"), cancellable = true)
  private void portalix$target(CallbackInfoReturnable<PositionAndRotation> cir) {
    var interpolation = portalix$activeInterpolation();
    if (interpolation != null) cir.setReturnValue(interpolation.target());
  }

  @Inject(method = "interpolate", at = @At("HEAD"), cancellable = true)
  private void portalix$skipNativeInterpolation(CallbackInfo ci) {
    var interpolation = portalix$activeInterpolation();
    if (interpolation != null
        && (interpolation.driving() || !entity.getInterpolation().hasActiveInterpolation())) ci.cancel();
  }
}
