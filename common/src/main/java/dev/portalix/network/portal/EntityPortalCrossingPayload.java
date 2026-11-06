package dev.portalix.network.portal;

import dev.portalix.PortalixMod;
import dev.portalix.network.PayloadCodecs;
import dev.portalix.portal.traversal.PortalEntityMovement.Crossing;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record EntityPortalCrossingPayload(
    int entityId,
    UUID entityUuid,
    List<Crossing> crossings,
    Vec3 position,
    Vec3 velocity,
    float yaw,
    float pitch,
    float headYaw,
    float bodyYaw,
    float poseScale)
    implements CustomPacketPayload {
  public EntityPortalCrossingPayload {
    crossings = List.copyOf(crossings);
    if (crossings.isEmpty()) throw new IllegalArgumentException("Empty portal route");
  }

  public static final Type<EntityPortalCrossingPayload> TYPE =
      new Type<>(PortalixMod.id("portal_entity_crossing"));
  public static final StreamCodec<RegistryFriendlyByteBuf, EntityPortalCrossingPayload> CODEC =
      CustomPacketPayload.codec(
          (payload, buffer) -> {
            buffer.writeVarInt(payload.entityId);
            buffer.writeUUID(payload.entityUuid);
            buffer.writeCollection(payload.crossings, EntityPortalCrossingPayload::writeCrossing);
            PayloadCodecs.writeVector(buffer, payload.position);
            PayloadCodecs.writeVector(buffer, payload.velocity);
            buffer.writeFloat(payload.yaw);
            buffer.writeFloat(payload.pitch);
            buffer.writeFloat(payload.headYaw);
            buffer.writeFloat(payload.bodyYaw);
            buffer.writeFloat(payload.poseScale);
          },
          buffer ->
              new EntityPortalCrossingPayload(
                  buffer.readVarInt(),
                  buffer.readUUID(),
                  buffer.readList(EntityPortalCrossingPayload::readCrossing),
                  PayloadCodecs.readVector(buffer),
                  PayloadCodecs.readVector(buffer),
                  buffer.readFloat(),
                  buffer.readFloat(),
                  buffer.readFloat(),
                  buffer.readFloat(),
                  buffer.readFloat()));

  private static void writeCrossing(FriendlyByteBuf buffer, Crossing crossing) {
    buffer.writeVarLong(crossing.eventId());
    PayloadCodecs.writePortalLink(buffer, crossing.link());
    PayloadCodecs.writeVector(buffer, crossing.beforeFeet());
    PayloadCodecs.writeVector(buffer, crossing.afterFeet());
    PayloadCodecs.writeVector(buffer, crossing.velocity());
    buffer.writeFloat(crossing.yaw());
    buffer.writeFloat(crossing.pitch());
    buffer.writeDouble(crossing.moveFraction());
    PayloadCodecs.writeVector(buffer, crossing.previousFeet());
    buffer.writeFloat(crossing.beforeEyeHeight());
    buffer.writeFloat(crossing.beforeYaw());
    buffer.writeFloat(crossing.beforePitch());
    buffer.writeFloat(crossing.poseScale());
  }

  private static Crossing readCrossing(FriendlyByteBuf buffer) {
    long event = buffer.readVarLong();
    var link = PayloadCodecs.readPortalLink(buffer);
    return new Crossing(
        event,
        link,
        PayloadCodecs.readVector(buffer),
        PayloadCodecs.readVector(buffer),
        PayloadCodecs.readVector(buffer),
        buffer.readFloat(),
        buffer.readFloat(),
        buffer.readDouble(),
        PayloadCodecs.readVector(buffer),
        buffer.readFloat(),
        buffer.readFloat(),
        buffer.readFloat(),
        buffer.readFloat());
  }

  @Override
  public Type<EntityPortalCrossingPayload> type() {
    return TYPE;
  }
}
