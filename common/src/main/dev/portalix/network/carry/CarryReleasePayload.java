package dev.portalix.network.carry;

import dev.portalix.PortalixMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record CarryReleasePayload(
    int entityId,
    UUID entityUuid,
    long generation,
    Vec3 position,
    Vec3 velocity,
    boolean thrown,
    long tick)
    implements CustomPacketPayload {
  public static final Type<CarryReleasePayload> TYPE = new Type<>(PortalixMod.id("carry_release"));
  public static final StreamCodec<RegistryFriendlyByteBuf, CarryReleasePayload> CODEC =
      CustomPacketPayload.codec(
          (release, buffer) -> {
            buffer.writeVarInt(release.entityId);
            buffer.writeUUID(release.entityUuid);
            buffer.writeVarLong(release.generation);
            Vec3.STREAM_CODEC.encode(buffer, release.position);
            Vec3.STREAM_CODEC.encode(buffer, release.velocity);
            buffer.writeBoolean(release.thrown);
            buffer.writeVarLong(release.tick);
          },
          buffer ->
              new CarryReleasePayload(
                  buffer.readVarInt(),
                  buffer.readUUID(),
                  buffer.readVarLong(),
                  Vec3.STREAM_CODEC.decode(buffer),
                  Vec3.STREAM_CODEC.decode(buffer),
                  buffer.readBoolean(),
                  buffer.readVarLong()));

  @Override
  public Type<CarryReleasePayload> type() {
    return TYPE;
  }
}
