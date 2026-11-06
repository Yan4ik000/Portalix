package dev.portalix.client.mixin.traversal;

import dev.portalix.client.portal.RemotePortalInterpolation;
import dev.portalix.portal.geometry.PortalTransform;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RemotePlayer.class)
abstract class PortalRemoteMotionMixin implements RemotePortalInterpolation.MotionAccess {
  @Shadow private Vec3 lerpDeltaMovement;

  @Override
  public void portalix$transformMotion(PortalTransform transform) {
    lerpDeltaMovement = transform.vector(lerpDeltaMovement);
  }
}
