package dev.portalix.network.gun;

import dev.portalix.PortalixMod;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record GunEventPayload(int playerId, GunEventKind kind) implements CustomPacketPayload {
  public GunEventPayload {
    if (playerId < 0) throw new IllegalArgumentException("Invalid event player ID");
    Objects.requireNonNull(kind, "Gun event kind");
  }

  public static final Type<GunEventPayload> TYPE = new Type<>(PortalixMod.id("event"));
  public static final StreamCodec<RegistryFriendlyByteBuf, GunEventPayload> CODEC =
      CustomPacketPayload.codec(
          (payload, buffer) -> {
            buffer.writeVarInt(payload.playerId);
            buffer.writeEnum(payload.kind);
          },
          buffer -> new GunEventPayload(buffer.readVarInt(), buffer.readEnum(GunEventKind.class)));

  public Type<GunEventPayload> type() {
    return TYPE;
  }
}
