package dev.portalix.network.portal;

import dev.portalix.PortalixMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record ProjectileStatePayload(
    int entityId,
    UUID entityUuid,
    long tick,
    long event,
    Vec3 position,
    Vec3 velocity,
    float yaw,
    float pitch)
    implements CustomPacketPayload {
  public static final Type<ProjectileStatePayload> TYPE =
      new Type<>(PortalixMod.id("portal_projectile_snapshot"));
  public static final StreamCodec<RegistryFriendlyByteBuf, ProjectileStatePayload> CODEC =
      CustomPacketPayload.codec(
          (packet, buffer) -> {
            buffer.writeVarInt(packet.entityId);
            buffer.writeUUID(packet.entityUuid);
            buffer.writeVarLong(packet.tick);
            buffer.writeVarLong(packet.event);
            Vec3.STREAM_CODEC.encode(buffer, packet.position);
            Vec3.STREAM_CODEC.encode(buffer, packet.velocity);
            buffer.writeFloat(packet.yaw);
            buffer.writeFloat(packet.pitch);
          },
          buffer ->
              new ProjectileStatePayload(
                  buffer.readVarInt(),
                  buffer.readUUID(),
                  buffer.readVarLong(),
                  buffer.readVarLong(),
                  Vec3.STREAM_CODEC.decode(buffer),
                  Vec3.STREAM_CODEC.decode(buffer),
                  buffer.readFloat(),
                  buffer.readFloat()));

  @Override
  public Type<ProjectileStatePayload> type() {
    return TYPE;
  }
}
