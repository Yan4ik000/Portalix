package dev.portalix.network.carry;

import dev.portalix.PortalixMod;
import dev.portalix.network.PayloadCodecs;
import dev.portalix.portal.PortalLinkId;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record CarryGrabPayload(
    int entityId,
    UUID entityUuid,
    long generation,
    int ownerId,
    UUID ownerUuid,
    long tick,
    Vec3 position,
    Vec3 velocity,
    List<PortalLinkId> route)
    implements CustomPacketPayload {
  private static final int MAX_ROUTE_LENGTH = 64;
  private static final StreamCodec<ByteBuf, List<PortalLinkId>> ROUTE_CODEC =
      PayloadCodecs.LINK_ID.apply(ByteBufCodecs.list(MAX_ROUTE_LENGTH));

  public CarryGrabPayload {
    route = List.copyOf(route);
    if (route.size() > MAX_ROUTE_LENGTH) throw new IllegalArgumentException("Carry route too long");
  }

  public static final Type<CarryGrabPayload> TYPE = new Type<>(PortalixMod.id("carry_grab"));
  public static final StreamCodec<RegistryFriendlyByteBuf, CarryGrabPayload> CODEC =
      CustomPacketPayload.codec(
          (payload, buffer) -> {
            buffer.writeVarInt(payload.entityId);
            buffer.writeUUID(payload.entityUuid);
            buffer.writeVarLong(payload.generation);
            buffer.writeVarInt(payload.ownerId);
            buffer.writeUUID(payload.ownerUuid);
            buffer.writeVarLong(payload.tick);
            Vec3.STREAM_CODEC.encode(buffer, payload.position);
            Vec3.STREAM_CODEC.encode(buffer, payload.velocity);
            ROUTE_CODEC.encode(buffer, payload.route);
          },
          buffer ->
              new CarryGrabPayload(
                  buffer.readVarInt(),
                  buffer.readUUID(),
                  buffer.readVarLong(),
                  buffer.readVarInt(),
                  buffer.readUUID(),
                  buffer.readVarLong(),
                  Vec3.STREAM_CODEC.decode(buffer),
                  Vec3.STREAM_CODEC.decode(buffer),
                  ROUTE_CODEC.decode(buffer)));

  @Override
  public Type<CarryGrabPayload> type() {
    return TYPE;
  }
}
