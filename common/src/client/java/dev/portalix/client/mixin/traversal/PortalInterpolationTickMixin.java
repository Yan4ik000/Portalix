package dev.portalix.client.mixin.traversal;

import dev.portalix.client.portal.RemotePortalInterpolation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class PortalInterpolationTickMixin {
  @Inject(method = "commonTick", at = @At("TAIL"))
  private void portalix$tickInterpolation(CallbackInfo ci) {
    var entity = (Entity) (Object) this;
    if (entity.getInterpolation() instanceof RemotePortalInterpolation.Access access) {
      var interpolation = access.portalix$existingInterpolation();
      if (interpolation != null) interpolation.tick();
    }
  }
}
