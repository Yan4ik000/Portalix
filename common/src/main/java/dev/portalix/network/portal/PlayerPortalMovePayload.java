package dev.portalix.network.portal;

import dev.portalix.PortalixMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

public record PlayerPortalMovePayload(
    long sequence,
    int entityId,
    UUID entityUuid,
    MoverType mover,
    Vec3 before,
    Vec3 movement,
    Vec3 velocity,
    boolean sneaking,
    Vec3 predicted,
    float poseScale,
    float beforeYaw,
    float beforePitch,
    float playerYaw,
    float playerPitch,
    boolean finishesTick,
    Pose finalPose,
    float finalScale)
    implements CustomPacketPayload {
  public static final Type<PlayerPortalMovePayload> TYPE =
      new Type<>(PortalixMod.id("portal_move"));
  public static final StreamCodec<RegistryFriendlyByteBuf, PlayerPortalMovePayload> CODEC =
      CustomPacketPayload.codec(
          (payload, buffer) -> {
            buffer.writeVarLong(payload.sequence);
            buffer.writeVarInt(payload.entityId);
            buffer.writeUUID(payload.entityUuid);
            buffer.writeEnum(payload.mover);
            Vec3.STREAM_CODEC.encode(buffer, payload.before);
            Vec3.STREAM_CODEC.encode(buffer, payload.movement);
            Vec3.STREAM_CODEC.encode(buffer, payload.velocity);
            buffer.writeBoolean(payload.sneaking);
            Vec3.STREAM_CODEC.encode(buffer, payload.predicted);
            buffer.writeFloat(payload.poseScale);
            buffer.writeFloat(payload.beforeYaw);
            buffer.writeFloat(payload.beforePitch);
            buffer.writeFloat(payload.playerYaw);
            buffer.writeFloat(payload.playerPitch);
            buffer.writeBoolean(payload.finishesTick);
            buffer.writeEnum(payload.finalPose);
            buffer.writeFloat(payload.finalScale);
          },
          buffer ->
              new PlayerPortalMovePayload(
                  buffer.readVarLong(),
                  buffer.readVarInt(),
                  buffer.readUUID(),
                  buffer.readEnum(MoverType.class),
                  Vec3.STREAM_CODEC.decode(buffer),
                  Vec3.STREAM_CODEC.decode(buffer),
                  Vec3.STREAM_CODEC.decode(buffer),
                  buffer.readBoolean(),
                  Vec3.STREAM_CODEC.decode(buffer),
                  buffer.readFloat(),
                  buffer.readFloat(),
                  buffer.readFloat(),
                  buffer.readFloat(),
                  buffer.readFloat(),
                  buffer.readBoolean(),
                  buffer.readEnum(Pose.class),
                  buffer.readFloat()));

  public PlayerPortalMovePayload withFinalPose(Pose pose, float scale) {
    return new PlayerPortalMovePayload(
        sequence,
        entityId,
        entityUuid,
        mover,
        before,
        movement,
        velocity,
        sneaking,
        predicted,
        poseScale,
        beforeYaw,
        beforePitch,
        playerYaw,
        playerPitch,
        true,
        pose,
        scale);
  }

  @Override
  public Type<PlayerPortalMovePayload> type() {
    return TYPE;
  }
}
