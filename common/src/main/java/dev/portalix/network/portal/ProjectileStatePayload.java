package dev.portalix.network.portal;

import dev.portalix.PortalixMod;
import dev.portalix.network.PayloadCodecs;
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
            PayloadCodecs.writeVector(buffer, packet.position);
            PayloadCodecs.writeVector(buffer, packet.velocity);
            buffer.writeFloat(packet.yaw);
            buffer.writeFloat(packet.pitch);
          },
          buffer ->
              new ProjectileStatePayload(
                  buffer.readVarInt(),
                  buffer.readUUID(),
                  buffer.readVarLong(),
                  buffer.readVarLong(),
                  PayloadCodecs.readVector(buffer),
                  PayloadCodecs.readVector(buffer),
                  buffer.readFloat(),
                  buffer.readFloat()));

  @Override
  public Type<ProjectileStatePayload> type() {
    return TYPE;
  }
}
