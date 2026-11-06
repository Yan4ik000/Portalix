package dev.portalix.network.carry;

import dev.portalix.PortalixMod;
import dev.portalix.network.PayloadCodecs;
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
    long sequence,
    long tick)
    implements CustomPacketPayload {
  public static final Type<CarryReleasePayload> TYPE = new Type<>(PortalixMod.id("carry_release"));
  public static final StreamCodec<RegistryFriendlyByteBuf, CarryReleasePayload> CODEC =
      CustomPacketPayload.codec(
          (release, buffer) -> {
            buffer.writeVarInt(release.entityId);
            buffer.writeUUID(release.entityUuid);
            buffer.writeVarLong(release.generation);
            PayloadCodecs.writeVector(buffer, release.position);
            PayloadCodecs.writeVector(buffer, release.velocity);
            buffer.writeBoolean(release.thrown);
            buffer.writeVarLong(release.sequence);
            buffer.writeVarLong(release.tick);
          },
          buffer ->
              new CarryReleasePayload(
                  buffer.readVarInt(),
                  buffer.readUUID(),
                  buffer.readVarLong(),
                  PayloadCodecs.readVector(buffer),
                  PayloadCodecs.readVector(buffer),
                  buffer.readBoolean(),
                  buffer.readVarLong(),
                  buffer.readVarLong()));

  @Override
  public Type<CarryReleasePayload> type() {
    return TYPE;
  }
}
