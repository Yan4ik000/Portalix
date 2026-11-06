package dev.portalix.network.portal;

import dev.portalix.PortalixMod;
import dev.portalix.network.PayloadCodecs;
import dev.portalix.portal.PortalLink;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PlayerPortalMoveAckPayload(long sequence, boolean accepted, float poseScale,
    List<PortalLink> crossings)
    implements CustomPacketPayload {
  public static final Type<PlayerPortalMoveAckPayload> TYPE =
      new Type<>(PortalixMod.id("portal_move_ack"));
  public static final StreamCodec<RegistryFriendlyByteBuf, PlayerPortalMoveAckPayload> CODEC =
      CustomPacketPayload.codec(
          (payload, buffer) -> {
            buffer.writeVarLong(payload.sequence);
            buffer.writeBoolean(payload.accepted);
            buffer.writeFloat(payload.poseScale);
            buffer.writeCollection(payload.crossings, PayloadCodecs::writePortalLink);
          },
          buffer ->
              new PlayerPortalMoveAckPayload(
                  buffer.readVarLong(), buffer.readBoolean(), buffer.readFloat(),
                  buffer.readList(PayloadCodecs::readPortalLink)));

  public PlayerPortalMoveAckPayload {
    crossings = List.copyOf(crossings);
  }

  @Override
  public Type<PlayerPortalMoveAckPayload> type() {
    return TYPE;
  }
}
