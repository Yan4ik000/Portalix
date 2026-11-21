package dev.portalix.network.portal;

import dev.portalix.PortalixMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PortalOwnedStatePayload(PortalStatePayload state, int ownedMask) implements CustomPacketPayload {
  public static final Type<PortalOwnedStatePayload> TYPE =
      new Type<>(PortalixMod.id("portals_owned"));
  public static final StreamCodec<RegistryFriendlyByteBuf, PortalOwnedStatePayload> CODEC =
      StreamCodec.composite(
          PortalStatePayload.CODEC, PortalOwnedStatePayload::state,
          ByteBufCodecs.VAR_INT, PortalOwnedStatePayload::ownedMask,
          PortalOwnedStatePayload::new);

  public PortalOwnedStatePayload {
    if ((ownedMask & ~3) != 0) throw new IllegalArgumentException("Invalid portal ownership mask");
  }

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
