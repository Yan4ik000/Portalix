package dev.portalix.network.carry;

import dev.portalix.PortalixMod;
import dev.portalix.network.PayloadCodecs;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record CarryStatePayload(
    int entityId,
    UUID entityUuid,
    long generation,
    long sequence,
    long tick,
    Vec3 position,
    Vec3 velocity,
    boolean released,
    boolean complete)
    implements CustomPacketPayload {
  public static final Type<CarryStatePayload> TYPE =
      new Type<>(PortalixMod.id("carry_motion_state"));
  public static final StreamCodec<RegistryFriendlyByteBuf, CarryStatePayload> CODEC =
      CustomPacketPayload.codec(
          (payload, buffer) -> {
            buffer.writeVarInt(payload.entityId);
            buffer.writeUUID(payload.entityUuid);
            buffer.writeVarLong(payload.generation);
            buffer.writeVarLong(payload.sequence);
            buffer.writeVarLong(payload.tick);
            PayloadCodecs.writeVector(buffer, payload.position);
            PayloadCodecs.writeVector(buffer, payload.velocity);
            buffer.writeBoolean(payload.released);
            buffer.writeBoolean(payload.complete);
          },
          buffer ->
              new CarryStatePayload(
                  buffer.readVarInt(),
                  buffer.readUUID(),
                  buffer.readVarLong(),
                  buffer.readVarLong(),
                  buffer.readVarLong(),
                  PayloadCodecs.readVector(buffer),
                  PayloadCodecs.readVector(buffer),
                  buffer.readBoolean(),
                  buffer.readBoolean()));

  @Override
  public Type<CarryStatePayload> type() {
    return TYPE;
  }
}
