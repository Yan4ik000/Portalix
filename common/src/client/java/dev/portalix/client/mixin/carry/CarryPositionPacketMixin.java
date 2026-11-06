package dev.portalix.client.mixin.carry;

import dev.portalix.client.PortalixClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class CarryPositionPacketMixin {
  @Inject(method = "handleEntityPositionSync", at = @At("TAIL"))
  private void portalix$sync(ClientboundEntityPositionSyncPacket packet, CallbackInfo ci) {
    var client = Minecraft.getInstance();
    var entity = client.level == null ? null : client.level.getEntity(packet.id());
    if (entity != null)
      PortalixClient.carryController().nativePosition(entity, packet.values().position());
  }

  @Inject(method = "handleMoveEntity", at = @At("TAIL"))
  private void portalix$move(ClientboundMoveEntityPacket packet, CallbackInfo ci) {
    if (!packet.hasPosition()) return;
    var client = Minecraft.getInstance();
    var entity = client.level == null ? null : packet.getEntity(client.level);
    if (entity != null)
      PortalixClient.carryController()
          .nativePosition(entity, entity.getPositionCodec().getBase());
  }

  @Inject(method = "handleTeleportEntity", at = @At("TAIL"))
  private void portalix$teleport(ClientboundTeleportEntityPacket packet, CallbackInfo ci) {
    var client = Minecraft.getInstance();
    var entity = client.level == null ? null : client.level.getEntity(packet.id());
    if (entity != null) {
      var interpolation = entity.getInterpolation();
      var position =
          interpolation != null && interpolation.hasActiveInterpolation()
              ? interpolation.position()
              : entity.position();
      PortalixClient.carryController().teleport(entity, position);
    }
  }
}
