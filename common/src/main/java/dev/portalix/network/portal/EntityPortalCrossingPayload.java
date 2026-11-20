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
    float poseScale,
    long resumeLoadingAt)
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
            Vec3.STREAM_CODEC.encode(buffer, payload.position);
            Vec3.STREAM_CODEC.encode(buffer, payload.velocity);
            buffer.writeFloat(payload.yaw);
            buffer.writeFloat(payload.pitch);
            buffer.writeFloat(payload.headYaw);
            buffer.writeFloat(payload.bodyYaw);
            buffer.writeFloat(payload.poseScale);
            buffer.writeVarLong(payload.resumeLoadingAt);
          },
          buffer ->
              new EntityPortalCrossingPayload(
                  buffer.readVarInt(),
                  buffer.readUUID(),
                  buffer.readList(EntityPortalCrossingPayload::readCrossing),
                  Vec3.STREAM_CODEC.decode(buffer),
                  Vec3.STREAM_CODEC.decode(buffer),
                  buffer.readFloat(),
                  buffer.readFloat(),
                  buffer.readFloat(),
                  buffer.readFloat(),
                  buffer.readFloat(),
                  buffer.readVarLong()));

  private static void writeCrossing(FriendlyByteBuf buffer, Crossing crossing) {
    buffer.writeVarLong(crossing.eventId());
    PayloadCodecs.writePortalLink(buffer, crossing.link());
    Vec3.STREAM_CODEC.encode(buffer, crossing.beforeFeet());
    Vec3.STREAM_CODEC.encode(buffer, crossing.afterFeet());
    Vec3.STREAM_CODEC.encode(buffer, crossing.velocity());
    buffer.writeFloat(crossing.yaw());
    buffer.writeFloat(crossing.pitch());
    buffer.writeDouble(crossing.moveFraction());
    Vec3.STREAM_CODEC.encode(buffer, crossing.previousFeet());
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
        Vec3.STREAM_CODEC.decode(buffer),
        Vec3.STREAM_CODEC.decode(buffer),
        Vec3.STREAM_CODEC.decode(buffer),
        buffer.readFloat(),
        buffer.readFloat(),
        buffer.readDouble(),
        Vec3.STREAM_CODEC.decode(buffer),
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
