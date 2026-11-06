package dev.portalix.client.mixin.traversal;

import dev.portalix.portal.traversal.PortalEntityCollisions;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class PortalPlayerPushOutMixin {
  @Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
  private void portalix$pushOut(double x, double z, CallbackInfo callback) {
    var player = (LocalPlayer) (Object) this;
    Boolean collision =
        PortalEntityCollisions.suffocates(player, player.getBoundingBox().deflate(1e-7));
    if (Boolean.FALSE.equals(collision)) callback.cancel();
  }
}
