package dev.portalix.mixin.world.tracking;

import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.server.portal.PlayerPortalMoveState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerPortalObservationMixin
    implements PlayerPortalVisibility.Access, PlayerPortalMoveState.Access {
  @Unique private final PlayerPortalMoveState portalix$transit = new PlayerPortalMoveState();

  @Override
  public PlayerPortalMoveState portalix$transit() {
    return portalix$transit;
  }

  @Inject(method = "setServerLevel", at = @At("HEAD"))
  private void portalix$world(ServerLevel level, CallbackInfo callback) {
    var player = (ServerPlayer) (Object) this;
    if (player.level() != level) {
      portalix$transit.resetForWorld();
      EntityPortalState.of(player).resetForWorld();
    }
  }

  @Unique private final PlayerPortalVisibility portalix$observation = new PlayerPortalVisibility();

  @Override
  public PlayerPortalVisibility portalix$observation() {
    return portalix$observation;
  }
}
