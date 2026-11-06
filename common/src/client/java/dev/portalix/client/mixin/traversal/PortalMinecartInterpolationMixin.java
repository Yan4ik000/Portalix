package dev.portalix.client.mixin.traversal;

import dev.portalix.client.portal.RemotePortalInterpolation;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NewMinecartBehavior.class)
abstract class PortalMinecartInterpolationMixin extends MinecartBehavior
    implements RemotePortalInterpolation.MinecartAccess {
  @Unique private RemotePortalInterpolation portalix$path;
  @Shadow private int lerpDelay;
  @Shadow private float cachedPartialTick;

  protected PortalMinecartInterpolationMixin(AbstractMinecart minecart) {
    super(minecart);
  }

  @Override
  public RemotePortalInterpolation portalix$interpolation() {
    if (portalix$path == null) portalix$path = new RemotePortalInterpolation(minecart);
    return portalix$path;
  }

  @Override
  public void portalix$resetInterpolation() {
    var behavior = (NewMinecartBehavior) (Object) this;
    behavior.lerpSteps.clear();
    behavior.currentLerpSteps.clear();
    behavior.currentLerpStepsTotalWeight = 0;
    behavior.setOldLerpValues();
    lerpDelay = 0;
    cachedPartialTick = Float.NaN;
  }

  @Inject(method = "lerpClientPositionAndRotation", at = @At("HEAD"), cancellable = true)
  private void portalix$tick(CallbackInfo ci) {
    if (portalix$path != null && portalix$path.active()) {
      portalix$path.tick();
      ci.cancel();
    }
  }

  @Inject(method = "cartHasPosRotLerp", at = @At("HEAD"), cancellable = true)
  private void portalix$render(CallbackInfoReturnable<Boolean> cir) {
    if (portalix$path != null && portalix$path.active()) cir.setReturnValue(false);
  }
}
