package dev.portalix.fabric;

import dev.portalix.PortalixMod;
import dev.portalix.network.ModNetworking;
import io.netty.buffer.ByteBuf;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

public record FabricProtocol(int version) implements CustomPacketPayload {
  public static final Type<FabricProtocol> TYPE = new Type<>(PortalixMod.id("protocol"));
  private static final StreamCodec<ByteBuf, FabricProtocol> CODEC =
      ByteBufCodecs.VAR_INT.map(FabricProtocol::new, FabricProtocol::version);

  static void register() {
    PayloadTypeRegistry.clientboundConfiguration().register(TYPE, CODEC);
    PayloadTypeRegistry.serverboundConfiguration().register(TYPE, CODEC);
    ServerConfigurationConnectionEvents.CONFIGURE.register((listener, server) -> {
      if (ServerConfigurationNetworking.canSend(listener, TYPE)) {
        listener.addTask(new VersionCheck(listener));
      } else {
        listener.disconnect(incompatibleMessage());
      }
    });
  }

  public static Component incompatibleMessage() {
    return Component.literal(
        "Incompatible Portalix network protocol. Use matching Portalix versions on the client and server.");
  }

  @Override
  public Type<FabricProtocol> type() {
    return TYPE;
  }

  private record VersionCheck(ServerConfigurationPacketListenerImpl listener)
      implements ConfigurationTask {
    private static final ConfigurationTask.Type TASK_TYPE =
        new ConfigurationTask.Type(FabricProtocol.TYPE.id().toString());

    @Override
    public void start(Consumer<Packet<?>> sender) {
      ServerConfigurationNetworking.registerReceiver(listener, TYPE, (payload, context) -> {
        ServerConfigurationNetworking.unregisterReceiver(listener, TYPE.id());
        if (payload.version() == ModNetworking.PROTOCOL_VERSION) {
          listener.completeTask(TASK_TYPE);
        } else {
          listener.disconnect(incompatibleMessage());
        }
      });
      sender.accept(ServerConfigurationNetworking.createClientboundPacket(
          new FabricProtocol(ModNetworking.PROTOCOL_VERSION)));
    }

    @Override
    public ConfigurationTask.Type type() {
      return TASK_TYPE;
    }
  }
}
