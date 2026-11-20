package dev.portalix.client.mixin.camera;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.portalix.client.portal.camera.PortalCameraController;
import dev.portalix.client.portal.ClientPortalMovement;
import dev.portalix.portal.traversal.EntityPortalState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class PortalCameraCorrectionMixin {
  @ModifyExpressionValue(method = "handleMovePlayer", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/world/entity/player/Player;isPassenger()Z"), require = 1, allow = 1)
  private boolean portalix$applyBundledRiderPosition(boolean passenger) {
    if (!passenger || ClientPortalMovement.hasPositionCorrection()) return false;
    ClientPortalMovement.corrected(Minecraft.getInstance().player);
    return true;
  }

  @Inject(method = "handleMovePlayer", at = @At("RETURN"))
  private void portalix$playerCorrection(
      ClientboundPlayerPositionPacket packet, CallbackInfo callback) {
    ClientPortalMovement.positionCorrected();
  }

  @Inject(method = "handleTeleportEntity", at = @At("RETURN"))
  private void portalix$entityCorrection(
      ClientboundTeleportEntityPacket packet, CallbackInfo callback) {
    var client = Minecraft.getInstance();
    var entity = client.level == null ? null : client.level.getEntity(packet.id());
    if (entity == null) return;
    entity
        .getSelfAndPassengers()
        .forEach(member -> EntityPortalState.of(member).resetCamera());
    if (entity == client.player
        || client.player != null && entity.hasIndirectPassenger(client.player)) {
      PortalCameraController.reset();
    }
  }
}
