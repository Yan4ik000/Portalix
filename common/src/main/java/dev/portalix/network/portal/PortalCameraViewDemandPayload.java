package dev.portalix.network.portal;

import dev.portalix.PortalixMod;
import dev.portalix.network.PayloadCodecs;
import dev.portalix.portal.PortalLinkId;
import dev.portalix.portal.PortalViewLimits;
import dev.portalix.portal.camera.PortalCameraDistance;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PortalCameraViewDemandPayload(
    PortalViewDemandPayload views, int detachedDistance, List<PortalLinkId> waitingPath)
    implements CustomPacketPayload {
  public static final Type<PortalCameraViewDemandPayload> TYPE =
      new Type<>(PortalixMod.id("portal_camera_view_demand"));
  public static final StreamCodec<RegistryFriendlyByteBuf, PortalCameraViewDemandPayload> CODEC =
      StreamCodec.composite(
          PortalViewDemandPayload.CODEC, PortalCameraViewDemandPayload::views,
          ByteBufCodecs.VAR_INT, PortalCameraViewDemandPayload::detachedDistance,
          PayloadCodecs.LINK_ID.apply(ByteBufCodecs.list(PortalViewLimits.CAMERA_DEPTH + 1)),
          PortalCameraViewDemandPayload::waitingPath,
          PortalCameraViewDemandPayload::new);

  public PortalCameraViewDemandPayload {
    detachedDistance = Math.clamp(detachedDistance, 0, PortalCameraDistance.MAX_MODDED_LOADING_DISTANCE);
    if (waitingPath.size() > PortalViewLimits.CAMERA_DEPTH + 1)
      throw new IllegalArgumentException("Invalid waiting camera path depth");
    waitingPath = List.copyOf(waitingPath);
  }

  @Override
  public Type<PortalCameraViewDemandPayload> type() {
    return TYPE;
  }
}
