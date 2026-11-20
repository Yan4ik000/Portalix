package dev.portalix.network.portal;

import dev.portalix.PortalixMod;
import dev.portalix.network.PayloadCodecs;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.traversal.PortalProjectileRotation;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.PositionMoveRotation;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

public record EntityPortalInitPayload(int entityId, UUID entityUuid, long event,
    PositionMoveRotation pose, boolean onGround, State state) implements CustomPacketPayload {
  public static final Type<EntityPortalInitPayload> TYPE =
      new Type<>(PortalixMod.id("portal_entity_init"));
  public static final StreamCodec<RegistryFriendlyByteBuf, EntityPortalInitPayload> CODEC =
      CustomPacketPayload.codec(
          (packet, buffer) -> {
            buffer.writeVarInt(packet.entityId);
            buffer.writeUUID(packet.entityUuid);
            buffer.writeVarLong(packet.event);
            PositionMoveRotation.STREAM_CODEC.encode(buffer, packet.pose);
            buffer.writeBoolean(packet.onGround);
            writeState(buffer, packet.state);
          },
          buffer -> new EntityPortalInitPayload(buffer.readVarInt(), buffer.readUUID(),
              buffer.readVarLong(), PositionMoveRotation.STREAM_CODEC.decode(buffer),
              buffer.readBoolean(), readState(buffer)));

  public EntityPortalInitPayload {
    if (event < 0) throw new IllegalArgumentException("Negative entity portal event: " + event);
    Objects.requireNonNull(state);
  }

  public sealed interface State permits Rotation, FishingTether {}

  public record Rotation(Quaternionfc rotation) implements State {
    public Rotation {
      if (!PortalProjectileRotation.isValid(rotation))
        throw new IllegalArgumentException("Invalid entity portal model rotation");
      rotation = new Quaternionf(rotation);
    }
  }

  public record FishingTether(List<PortalLink> route, long completedOwnerMove,
      List<PortalLink> activeOwnerCrossings) implements State {
    public FishingTether {
      if (completedOwnerMove < 0) throw new IllegalArgumentException("Negative completed owner move: " + completedOwnerMove);
      route = List.copyOf(route);
      activeOwnerCrossings = List.copyOf(activeOwnerCrossings);
    }
  }

  private static void writeState(FriendlyByteBuf buffer, State state) {
    switch (state) {
      case Rotation rotation -> {
        buffer.writeByte(0);
        FriendlyByteBuf.writeQuaternion(buffer, rotation.rotation());
      }
      case FishingTether tether -> {
        buffer.writeByte(1);
        buffer.writeCollection(tether.route(), PayloadCodecs::writePortalLink);
        buffer.writeVarLong(tether.completedOwnerMove());
        buffer.writeCollection(tether.activeOwnerCrossings(), PayloadCodecs::writePortalLink);
      }
    }
  }

  private static State readState(FriendlyByteBuf buffer) {
    int id = buffer.readUnsignedByte();
    return switch (id) {
      case 0 -> new Rotation(buffer.readQuaternion());
      case 1 -> new FishingTether(buffer.readList(PayloadCodecs::readPortalLink), buffer.readVarLong(),
          buffer.readList(PayloadCodecs::readPortalLink));
      default -> throw new IllegalArgumentException("Unknown entity portal initial state: " + id);
    };
  }

  @Override
  public Type<EntityPortalInitPayload> type() {
    return TYPE;
  }
}
