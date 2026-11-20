package dev.portalix.network.portal;

import dev.portalix.PortalixMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ProjectilePortalMovePayload(int entityId, UUID entityUuid)
    implements CustomPacketPayload {
  public static final Type<ProjectilePortalMovePayload> TYPE =
      new Type<>(PortalixMod.id("portal_projectile_request"));
  public static final StreamCodec<RegistryFriendlyByteBuf, ProjectilePortalMovePayload> CODEC =
      CustomPacketPayload.codec(
          (packet, buffer) -> {
            buffer.writeVarInt(packet.entityId);
            buffer.writeUUID(packet.entityUuid);
          },
          buffer -> new ProjectilePortalMovePayload(buffer.readVarInt(), buffer.readUUID()));

  @Override
  public Type<ProjectilePortalMovePayload> type() {
    return TYPE;
  }
}
