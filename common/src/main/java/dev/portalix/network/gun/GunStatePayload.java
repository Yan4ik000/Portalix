package dev.portalix.network.gun;

import dev.portalix.PortalixMod;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record GunStatePayload(
    int playerId,
    UUID owner,
    int targetId,
    UUID target,
    int holdTicks,
    int activeMask,
    boolean orange)
    implements CustomPacketPayload {
  public static final UUID EMPTY_TARGET = new UUID(0, 0);

  public GunStatePayload {
    Objects.requireNonNull(owner, "Snapshot owner");
    if (playerId < 0 || targetId < -1 || holdTicks < 0 || (activeMask & ~3) != 0)
      throw new IllegalArgumentException("Invalid gun snapshot");
    if (targetId >= 0) Objects.requireNonNull(target, "Held target UUID");
    else {
      target = EMPTY_TARGET;
      holdTicks = 0;
    }
  }

  public static final Type<GunStatePayload> TYPE = new Type<>(PortalixMod.id("state"));
  public static final StreamCodec<RegistryFriendlyByteBuf, GunStatePayload> CODEC =
      CustomPacketPayload.codec(
          (state, buffer) -> {
            buffer.writeVarInt(state.playerId);
            buffer.writeUUID(state.owner);
            buffer.writeBoolean(state.targetId >= 0);
            if (state.targetId >= 0) {
              buffer.writeVarInt(state.targetId);
              buffer.writeUUID(state.target);
              buffer.writeVarInt(state.holdTicks);
            }
            buffer.writeVarInt(state.activeMask);
            buffer.writeBoolean(state.orange);
          },
          buffer -> {
            int playerId = buffer.readVarInt();
            UUID owner = buffer.readUUID();
            boolean hasTarget = buffer.readBoolean();
            int targetId = hasTarget ? buffer.readVarInt() : -1;
            UUID target = hasTarget ? buffer.readUUID() : EMPTY_TARGET;
            int holdTicks = hasTarget ? buffer.readVarInt() : 0;
            return new GunStatePayload(
                playerId,
                owner,
                targetId,
                target,
                holdTicks,
                buffer.readVarInt(),
                buffer.readBoolean());
          });

  public Type<GunStatePayload> type() {
    return TYPE;
  }
}
