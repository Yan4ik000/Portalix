package dev.portalix.neoforge.client;

import dev.portalix.client.ClientNetworking;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

public final class NeoForgeClientNetworking implements ClientNetworking.Registration {
  private final RegisterClientPayloadHandlersEvent event;

  public NeoForgeClientNetworking(RegisterClientPayloadHandlersEvent event) {
    this.event = event;
  }

  @Override
  public <T extends CustomPacketPayload> void register(
      CustomPacketPayload.Type<T> type, BiConsumer<T, Minecraft> receiver) {
    event.register(type, (payload, context) -> receiver.accept(payload, Minecraft.getInstance()));
  }
}
