package dev.portalix.client.mixin.traversal;

import dev.portalix.client.portal.RemotePortalInterpolation;
import dev.portalix.portal.traversal.PortalPassagePose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Player.class)
abstract class PortalPoseAttachmentMixin implements PortalPassagePose.NetworkAccess {
  @Override
  public boolean portalix$acceptPoseScale(float scale) {
    Player player = (Player) (Object) this;
    if (player.isLocalInstanceAuthoritative() && !player.isPassenger()) return false;
    var interpolation = RemotePortalInterpolation.of(player);
    if (interpolation != null && interpolation.active()) {
      interpolation.scale(scale);
      return false;
    }
    return true;
  }
}
