package dev.portalix.client.mixin.camera;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.portal.camera.PortalCameraController;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.traversal.PortalPassagePose;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class PortalCameraCorrectionMixin {
  @WrapOperation(
      method = "handleSetEntityData",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/syncher/SynchedEntityData;assignValues(Ljava/util/List;)V"))
  private void portalix$predictedPose(
      SynchedEntityData data,
      List<SynchedEntityData.DataValue<?>> values,
      Operation<Void> original,
      ClientboundSetEntityDataPacket packet) {
    var player = Minecraft.getInstance().player;

    if (player != null && packet.id() == player.getId() && !player.isPassenger())
      values =
          values.stream()
              .filter(value -> !PortalPassagePose.isPoseField(player, value.id()))
              .toList();
    original.call(data, values);
  }

  @Inject(method = "handleMovePlayer", at = @At("RETURN"))
  private void portalix$playerCorrection(
      ClientboundPlayerPositionPacket packet, CallbackInfo callback) {
    PortalCameraController.reset();
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
