package dev.portalix.mixin.physics;

import dev.portalix.portal.physics.PortalEntityFunneling;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class PortalEntityFunnelingMixin {
  @Inject(method = "baseTick", at = @At("TAIL"))
  private void portalix$funnel(CallbackInfo callback) {
    PortalEntityFunneling.tick((Entity) (Object) this);
  }
}
