package dev.portalix.network.portal;

import dev.portalix.PortalixMod;
import dev.portalix.network.PayloadCodecs;
import dev.portalix.portal.PortalLinkId;
import dev.portalix.portal.PortalViewLimits;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PortalViewDemandPayload(List<PortalLinkId> cameraFrame, List<List<PortalLinkId>> routes)
    implements CustomPacketPayload {
  public static final Type<PortalViewDemandPayload> TYPE =
      new Type<>(PortalixMod.id("portal_view_demand"));
  public static final StreamCodec<RegistryFriendlyByteBuf, PortalViewDemandPayload> CODEC =
      StreamCodec.composite(
          PayloadCodecs.LINK_ID.apply(ByteBufCodecs.list(PortalViewLimits.CAMERA_DEPTH)),
          PortalViewDemandPayload::cameraFrame,
          PayloadCodecs.LINK_ID.apply(ByteBufCodecs.list(PortalViewLimits.DEPTH))
              .apply(ByteBufCodecs.list(PortalViewLimits.ROUTES)),
          PortalViewDemandPayload::routes,
          PortalViewDemandPayload::new);

  public PortalViewDemandPayload {
    if (cameraFrame.size() > PortalViewLimits.CAMERA_DEPTH)
      throw new IllegalArgumentException("Invalid camera frame depth");
    cameraFrame = List.copyOf(cameraFrame);
    if (routes.size() > PortalViewLimits.ROUTES)
      throw new IllegalArgumentException("Too many portal views");
    routes = routes.stream().map(route -> {
      if (route.isEmpty() || route.size() > PortalViewLimits.DEPTH)
        throw new IllegalArgumentException("Invalid portal view depth");
      return List.copyOf(route);
    }).toList();
  }

  @Override
  public Type<PortalViewDemandPayload> type() {
    return TYPE;
  }
}
