package dev.portalix.network.gun;

import dev.portalix.PortalixMod;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record GunCommandPayload(GunCommand command) implements CustomPacketPayload {
  public GunCommandPayload {
    Objects.requireNonNull(command, "Gun command");
  }

  public static final Type<GunCommandPayload> TYPE = new Type<>(PortalixMod.id("action"));
  public static final StreamCodec<RegistryFriendlyByteBuf, GunCommandPayload> CODEC =
      CustomPacketPayload.codec(
          (payload, buffer) -> buffer.writeEnum(payload.command),
          buffer -> new GunCommandPayload(buffer.readEnum(GunCommand.class)));

  public Type<GunCommandPayload> type() {
    return TYPE;
  }
}
