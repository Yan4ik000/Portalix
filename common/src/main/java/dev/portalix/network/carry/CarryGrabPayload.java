package dev.portalix.network.carry;

import dev.portalix.PortalixMod;
import dev.portalix.network.PayloadCodecs;
import dev.portalix.portal.PortalLinkId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record CarryGrabPayload(
    int entityId,
    UUID entityUuid,
    long generation,
    long sequence,
    int ownerId,
    UUID ownerUuid,
    long tick,
    Vec3 position,
    Vec3 velocity,
    List<PortalLinkId> route)
    implements CustomPacketPayload {
  public CarryGrabPayload {
    route = List.copyOf(route);
    if (route.size() > 64) throw new IllegalArgumentException("Carry route too long");
  }

  public static final Type<CarryGrabPayload> TYPE = new Type<>(PortalixMod.id("carry_grab"));
  public static final StreamCodec<RegistryFriendlyByteBuf, CarryGrabPayload> CODEC =
      CustomPacketPayload.codec(
          (payload, buffer) -> {
            buffer.writeVarInt(payload.entityId);
            buffer.writeUUID(payload.entityUuid);
            buffer.writeVarLong(payload.generation);
            buffer.writeVarLong(payload.sequence);
            buffer.writeVarInt(payload.ownerId);
            buffer.writeUUID(payload.ownerUuid);
            buffer.writeVarLong(payload.tick);
            PayloadCodecs.writeVector(buffer, payload.position);
            PayloadCodecs.writeVector(buffer, payload.velocity);
            buffer.writeVarInt(payload.route.size());
            for (var link : payload.route) PayloadCodecs.writeLink(buffer, link);
          },
          buffer ->
              new CarryGrabPayload(
                  buffer.readVarInt(),
                  buffer.readUUID(),
                  buffer.readVarLong(),
                  buffer.readVarLong(),
                  buffer.readVarInt(),
                  buffer.readUUID(),
                  buffer.readVarLong(),
                  PayloadCodecs.readVector(buffer),
                  PayloadCodecs.readVector(buffer),
                  readRoute(buffer)));

  private static List<PortalLinkId> readRoute(RegistryFriendlyByteBuf buffer) {
    int count = buffer.readVarInt();
    if (count < 0 || count > 64) throw new IllegalArgumentException("Invalid carry route");
    var route = new ArrayList<PortalLinkId>();
    for (int i = 0; i < count; i++) route.add(PayloadCodecs.readLink(buffer));
    return List.copyOf(route);
  }

  @Override
  public Type<CarryGrabPayload> type() {
    return TYPE;
  }
}
