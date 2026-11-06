package dev.portalix.network.portal;

import dev.portalix.PortalixMod;
import dev.portalix.network.PayloadCodecs;
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
    boolean grounded,
    Pose beforePose,
    float beforeScale,
    boolean sneaking,
    Vec3 predicted,
    float poseScale,
    float beforeYaw,
    float beforePitch,
    float playerYaw,
    float playerPitch,
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
            PayloadCodecs.writeVector(buffer, payload.before);
            PayloadCodecs.writeVector(buffer, payload.movement);
            PayloadCodecs.writeVector(buffer, payload.velocity);
            buffer.writeBoolean(payload.grounded);
            buffer.writeEnum(payload.beforePose);
            buffer.writeFloat(payload.beforeScale);
            buffer.writeBoolean(payload.sneaking);
            PayloadCodecs.writeVector(buffer, payload.predicted);
            buffer.writeFloat(payload.poseScale);
            buffer.writeFloat(payload.beforeYaw);
            buffer.writeFloat(payload.beforePitch);
            buffer.writeFloat(payload.playerYaw);
            buffer.writeFloat(payload.playerPitch);
            buffer.writeEnum(payload.finalPose);
            buffer.writeFloat(payload.finalScale);
          },
          buffer ->
              new PlayerPortalMovePayload(
                  buffer.readVarLong(),
                  buffer.readVarInt(),
                  buffer.readUUID(),
                  buffer.readEnum(MoverType.class),
                  PayloadCodecs.readVector(buffer),
                  PayloadCodecs.readVector(buffer),
                  PayloadCodecs.readVector(buffer),
                  buffer.readBoolean(),
                  buffer.readEnum(Pose.class),
                  buffer.readFloat(),
                  buffer.readBoolean(),
                  PayloadCodecs.readVector(buffer),
                  buffer.readFloat(),
                  buffer.readFloat(),
                  buffer.readFloat(),
                  buffer.readFloat(),
                  buffer.readFloat(),
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
        grounded,
        beforePose,
        beforeScale,
        sneaking,
        predicted,
        poseScale,
        beforeYaw,
        beforePitch,
        playerYaw,
        playerPitch,
        pose,
        scale);
  }

  @Override
  public Type<PlayerPortalMovePayload> type() {
    return TYPE;
  }
}
