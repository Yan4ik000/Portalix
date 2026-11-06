package dev.portalix.neoforge;

import dev.portalix.network.ModNetworking;
import java.util.function.BiConsumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NeoForgeNetworking implements ModNetworking.Registration {
  private final PayloadRegistrar registrar;

  public NeoForgeNetworking(PayloadRegistrar registrar) {
    this.registrar = registrar;
  }

  @Override
  public <T extends CustomPacketPayload> void clientbound(
      CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
    registrar.playToClient(type, codec);
  }

  @Override
  public <T extends CustomPacketPayload> void serverbound(
      CustomPacketPayload.Type<T> type,
      StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
      BiConsumer<ServerPlayer, T> receiver) {
    registrar.playToServer(type, codec,
        (payload, context) -> receiver.accept((ServerPlayer) context.player(), payload));
  }
}
