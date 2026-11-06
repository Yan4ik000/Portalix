package dev.portalix.client;

import dev.portalix.client.platform.ClientPlatform;
import dev.portalix.client.portal.ClientPortalMovement;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class ClientNetworking {
  private ClientNetworking() {}

  public interface Registration {
    <T extends CustomPacketPayload> void register(
        CustomPacketPayload.Type<T> type, BiConsumer<T, Minecraft> receiver);
  }

  public static void register(Registration registration) {
    ClientPayloadHandlers.register(registration);
    ClientPortalMovement.register(registration);
  }

  public static boolean canServerReceive(CustomPacketPayload.Type<?> type) {
    return ClientPlatform.INSTANCE.canServerReceive(type);
  }

  public static void sendToServer(CustomPacketPayload payload) {
    Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(payload));
  }
}
