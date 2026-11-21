package dev.portalix.network.portal;

import dev.portalix.PortalixMod;
import dev.portalix.portal.PortalViewLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PortalViewSettingsPayload(int ownRadius, int otherRadius) implements CustomPacketPayload {
  public static final Type<PortalViewSettingsPayload> TYPE =
      new Type<>(PortalixMod.id("portal_view_settings"));
  public static final StreamCodec<RegistryFriendlyByteBuf, PortalViewSettingsPayload> CODEC =
      StreamCodec.composite(
          ByteBufCodecs.VAR_INT, PortalViewSettingsPayload::ownRadius,
          ByteBufCodecs.VAR_INT, PortalViewSettingsPayload::otherRadius,
          PortalViewSettingsPayload::new);

  public PortalViewSettingsPayload {
    if (ownRadius < PortalViewLimits.MIN_PREVIEW_RADIUS || ownRadius > PortalViewLimits.MAX_PREVIEW_RADIUS
        || otherRadius < PortalViewLimits.MIN_PREVIEW_RADIUS || otherRadius > PortalViewLimits.MAX_PREVIEW_RADIUS)
      throw new IllegalArgumentException("Portal view settings out of range");
  }

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
