package dev.portalix.network.gun;

import dev.portalix.PortalixMod;
import dev.portalix.item.PortalixVariant;
import dev.portalix.network.PayloadCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.world.phys.Vec3;

public record GunShotPayload(
    int playerId,
    Vec3 start,
    Vec3 end,
    Vec3 normal,
    boolean orange,
    Vec3 launchDirection,
    long firedServerTick,
    int impactAfterTicks,
    long sequence,
    PortalixVariant variant)
    implements CustomPacketPayload {
  public static final Type<GunShotPayload> TYPE = new Type<>(PortalixMod.id("shot"));
  public static final StreamCodec<RegistryFriendlyByteBuf, GunShotPayload> CODEC =
      CustomPacketPayload.codec(
          (payload, buffer) -> {
            buffer.writeVarInt(payload.playerId);
            PayloadCodecs.writeVector(buffer, payload.start);
            PayloadCodecs.writeVector(buffer, payload.end);
            PayloadCodecs.writeVector(buffer, payload.normal);
            buffer.writeBoolean(payload.orange);
            PayloadCodecs.writeVector(buffer, payload.launchDirection);
            buffer.writeLong(payload.firedServerTick);
            buffer.writeVarInt(payload.impactAfterTicks);
            buffer.writeLong(payload.sequence);
            buffer.writeEnum(payload.variant);
          },
          buffer ->
              new GunShotPayload(
                  buffer.readVarInt(),
                  PayloadCodecs.readVector(buffer),
                  PayloadCodecs.readVector(buffer),
                  PayloadCodecs.readVector(buffer),
                  buffer.readBoolean(),
                  PayloadCodecs.readVector(buffer),
                  buffer.readLong(),
                  buffer.readVarInt(),
                  buffer.readLong(),
                  buffer.readEnum(PortalixVariant.class)));

  public Type<GunShotPayload> type() {
    return TYPE;
  }
}
