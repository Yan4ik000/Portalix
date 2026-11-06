package dev.portalix.fabric.client;

import dev.portalix.client.ClientNetworking;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class FabricClientNetworking implements ClientNetworking.Registration {
  @Override
  public <T extends CustomPacketPayload> void register(
      CustomPacketPayload.Type<T> type, BiConsumer<T, Minecraft> receiver) {
    ClientPlayNetworking.registerGlobalReceiver(type,
        (payload, context) -> receiver.accept(payload, context.client()));
  }
}
