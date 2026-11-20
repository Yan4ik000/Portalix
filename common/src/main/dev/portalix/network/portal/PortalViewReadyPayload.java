package dev.portalix.network.portal;

import dev.portalix.PortalixMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PortalViewReadyPayload(long entrance, long generation)
    implements CustomPacketPayload {
  public static final Type<PortalViewReadyPayload> TYPE =
      new Type<>(PortalixMod.id("portal_view_ready"));
  public static final StreamCodec<RegistryFriendlyByteBuf, PortalViewReadyPayload> CODEC =
      CustomPacketPayload.codec(
          (payload, buffer) -> {
            buffer.writeVarLong(payload.entrance);
            buffer.writeVarLong(payload.generation);
          },
          buffer -> new PortalViewReadyPayload(buffer.readVarLong(), buffer.readVarLong()));

  @Override
  public Type<PortalViewReadyPayload> type() {
    return TYPE;
  }
}
