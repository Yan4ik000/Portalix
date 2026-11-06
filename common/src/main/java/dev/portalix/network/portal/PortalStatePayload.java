package dev.portalix.network.portal;

import dev.portalix.PortalixMod;
import dev.portalix.api.PortalInstance;
import dev.portalix.network.PayloadCodecs;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PortalStatePayload(
    UUID gun,
    long revision,
    long serverTick,
    PortalInstance blue,
    PortalInstance orange,
    boolean effects)
    implements CustomPacketPayload {

  public static final Type<PortalStatePayload> TYPE = new Type<>(PortalixMod.id("portals"));
  public static final StreamCodec<RegistryFriendlyByteBuf, PortalStatePayload> CODEC =
      CustomPacketPayload.codec(
          (payload, buffer) -> {
            buffer.writeUUID(payload.gun);
            buffer.writeLong(payload.revision);
            buffer.writeLong(payload.serverTick);
            write(buffer, payload.blue);
            write(buffer, payload.orange);
            buffer.writeBoolean(payload.effects);
          },
          buffer ->
              new PortalStatePayload(
                  buffer.readUUID(),
                  buffer.readLong(),
                  buffer.readLong(),
                  read(buffer),
                  read(buffer),
                  buffer.readBoolean()));

  private static void write(RegistryFriendlyByteBuf buffer, PortalInstance member) {
    buffer.writeBoolean(member != null);
    if (member == null) return;
    PayloadCodecs.writePortal(buffer, member);
  }

  private static PortalInstance read(RegistryFriendlyByteBuf buffer) {
    return buffer.readBoolean() ? PayloadCodecs.readPortal(buffer) : null;
  }

  @Override
  public Type<PortalStatePayload> type() {
    return TYPE;
  }
}
