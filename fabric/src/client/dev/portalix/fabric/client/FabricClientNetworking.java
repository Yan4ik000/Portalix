package dev.portalix.fabric.client;

import dev.portalix.PortalixMod;
import dev.portalix.client.ClientNetworking;
import dev.portalix.fabric.FabricProtocol;
import dev.portalix.network.ModNetworking;
import dev.portalix.network.gun.GunCommandPayload;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

final class FabricClientNetworking implements ClientNetworking.Registration {
  private static final PacketContext.Key<Boolean> PROTOCOL_VERIFIED =
      PacketContext.key(PortalixMod.id("protocol_verified"));

  static void registerProtocolCheck() {
    ClientConfigurationNetworking.registerGlobalReceiver(FabricProtocol.TYPE, (payload, context) -> {
      context.packetContext().set(PROTOCOL_VERIFIED, payload.version() == ModNetworking.PROTOCOL_VERSION);
      context.responseSender().sendPacket(new FabricProtocol(ModNetworking.PROTOCOL_VERSION));
    });
    ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
      // Older Portalix servers advertise the play channel without a version check.
      if (!listener.getPacketContext().orElse(PROTOCOL_VERIFIED, false)
          && ClientPlayNetworking.canSend(GunCommandPayload.TYPE)) {
        listener.getConnection().disconnect(FabricProtocol.incompatibleMessage());
      }
    });
  }

  @Override
  public <T extends CustomPacketPayload> void register(
      CustomPacketPayload.Type<T> type, BiConsumer<T, Minecraft> receiver) {
    ClientPlayNetworking.registerGlobalReceiver(type,
        (payload, context) -> receiver.accept(payload, context.client()));
  }
}
