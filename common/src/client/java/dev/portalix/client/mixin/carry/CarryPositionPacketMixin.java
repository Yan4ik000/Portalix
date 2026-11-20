package dev.portalix.client.mixin.carry;

import dev.portalix.client.PortalixClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class CarryPositionPacketMixin {
  @Inject(method = "handleTeleportEntity", at = @At("TAIL"))
  private void portalix$teleport(ClientboundTeleportEntityPacket packet, CallbackInfo ci) {
    var client = Minecraft.getInstance();
    var entity = client.level == null ? null : client.level.getEntity(packet.id());
    if (entity != null) {
      PortalixClient.carryController().teleport(entity, entity.getClientPosition());
    }
  }
}
