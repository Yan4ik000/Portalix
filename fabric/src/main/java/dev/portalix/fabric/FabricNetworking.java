package dev.portalix.fabric;

import dev.portalix.network.ModNetworking;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class FabricNetworking implements ModNetworking.Registration {
  @Override
  public <T extends CustomPacketPayload> void clientbound(
      CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
    PayloadTypeRegistry.clientboundPlay().register(type, codec);
  }

  @Override
  public <T extends CustomPacketPayload> void serverbound(
      CustomPacketPayload.Type<T> type,
      StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
      BiConsumer<ServerPlayer, T> receiver) {
    PayloadTypeRegistry.serverboundPlay().register(type, codec);
    ServerPlayNetworking.registerGlobalReceiver(type,
        (payload, context) -> receiver.accept(context.player(), payload));
  }
}
