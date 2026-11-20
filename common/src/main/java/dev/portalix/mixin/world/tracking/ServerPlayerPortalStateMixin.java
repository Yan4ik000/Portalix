package dev.portalix.mixin.world.tracking;

import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.traversal.PortalPassagePose;
import dev.portalix.server.portal.PlayerPortalMoveState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerPortalStateMixin
    implements PlayerPortalVisibility.Access, PlayerPortalMoveState.Access {
  @Unique private final PlayerPortalMoveState portalix$movement = new PlayerPortalMoveState(Util.getMillis());
  @Unique private final PlayerPortalVisibility portalix$observation = new PlayerPortalVisibility();

  @Override
  public PlayerPortalMoveState portalix$movementState() {
    return portalix$movement;
  }

  @Override
  public PlayerPortalVisibility portalix$observation() {
    return portalix$observation;
  }

  @Inject(method = "setServerLevel", at = @At("HEAD"))
  private void portalix$resetPortalState(ServerLevel level, CallbackInfo ci) {
    var player = (ServerPlayer) (Object) this;
    if (player.level() != level) {
      PortalPassagePose.set(player, false);
      portalix$movement.resetForWorld();
      portalix$observation.clearReady();
      portalix$observation.resumeLoading();
      EntityPortalState.of(player).resetForWorld();
    }
  }
}
