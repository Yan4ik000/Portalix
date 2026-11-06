package dev.portalix.network.gun;

import dev.portalix.PortalixMod;
import dev.portalix.item.PortalixVariant;
import dev.portalix.network.PayloadCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record PortalImpactPayload(
    long sequence,
    Vec3 origin,
    Vec3 position,
    Vec3 normal,
    boolean orange,
    boolean placed,
    long serverTick,
    PortalixVariant variant)
    implements CustomPacketPayload {
  public static final Type<PortalImpactPayload> TYPE =
      new Type<>(PortalixMod.id("portal_impact"));
  public static final StreamCodec<RegistryFriendlyByteBuf, PortalImpactPayload> CODEC =
      CustomPacketPayload.codec(
          (payload, buffer) -> {
            buffer.writeLong(payload.sequence);
            PayloadCodecs.writeVector(buffer, payload.origin);
            PayloadCodecs.writeVector(buffer, payload.position);
            PayloadCodecs.writeVector(buffer, payload.normal);
            buffer.writeBoolean(payload.orange);
            buffer.writeBoolean(payload.placed);
            buffer.writeLong(payload.serverTick);
            buffer.writeEnum(payload.variant);
          },
          buffer ->
              new PortalImpactPayload(
                  buffer.readLong(),
                  PayloadCodecs.readVector(buffer),
                  PayloadCodecs.readVector(buffer),
                  PayloadCodecs.readVector(buffer),
                  buffer.readBoolean(),
                  buffer.readBoolean(),
                  buffer.readLong(),
                  buffer.readEnum(PortalixVariant.class)));

  @Override
  public Type<PortalImpactPayload> type() {
    return TYPE;
  }
}
