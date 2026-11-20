package dev.portalix.network.portal;

import dev.portalix.PortalixMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PortalViewStatusPayload(long entrance, long generation, boolean ready)
    implements CustomPacketPayload {
  public static final Type<PortalViewStatusPayload> TYPE =
      new Type<>(PortalixMod.id("portal_view_status"));
  public static final StreamCodec<RegistryFriendlyByteBuf, PortalViewStatusPayload> CODEC =
      StreamCodec.composite(
          ByteBufCodecs.VAR_LONG, PortalViewStatusPayload::entrance,
          ByteBufCodecs.VAR_LONG, PortalViewStatusPayload::generation,
          ByteBufCodecs.BOOL, PortalViewStatusPayload::ready,
          PortalViewStatusPayload::new);

  @Override
  public Type<PortalViewStatusPayload> type() {
    return TYPE;
  }
}
