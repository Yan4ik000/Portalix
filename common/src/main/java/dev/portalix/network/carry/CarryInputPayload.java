package dev.portalix.network.carry;

import dev.portalix.PortalixMod;
import dev.portalix.carry.CarryMotionPath;
import dev.portalix.network.PayloadCodecs;
import dev.portalix.portal.PortalLinkId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record CarryInputPayload(
    int entityId, UUID entityUuid, long generation, long sequence, List<Node> nodes)
    implements CustomPacketPayload {
  public static final int MAX_POINTS = CarryMotionPath.MAX_POINTS;

  public CarryInputPayload {
    nodes = List.copyOf(nodes);
    if (nodes.isEmpty() || nodes.size() > MAX_POINTS || nodes.getFirst().crossing() != null)
      throw new IllegalArgumentException("Invalid carry path size");
  }

  public static final Type<CarryInputPayload> TYPE = new Type<>(PortalixMod.id("carry_path"));
  public static final StreamCodec<RegistryFriendlyByteBuf, CarryInputPayload> CODEC =
      CustomPacketPayload.codec(
          (payload, buffer) -> {
            buffer.writeVarInt(payload.entityId);
            buffer.writeUUID(payload.entityUuid);
            buffer.writeVarLong(payload.generation);
            buffer.writeVarLong(payload.sequence);
            buffer.writeVarInt(payload.nodes.size());
            for (Node node : payload.nodes) {
              PayloadCodecs.writeVector(buffer, node.point());
              buffer.writeBoolean(node.crossing() != null);
              if (node.crossing() != null) PayloadCodecs.writeLink(buffer, node.crossing());
            }
          },
          buffer -> {
            int id = buffer.readVarInt();
            UUID uuid = buffer.readUUID();
            long generation = buffer.readVarLong();
            long sequence = buffer.readVarLong();
            int size = buffer.readVarInt();
            if (size < 1 || size > MAX_POINTS)
              throw new IllegalArgumentException("Invalid carry path size");
            var path = new ArrayList<Node>(size);
            for (int i = 0; i < size; i++)
              path.add(
                  new Node(
                      PayloadCodecs.readVector(buffer),
                      buffer.readBoolean() ? PayloadCodecs.readLink(buffer) : null));
            return new CarryInputPayload(id, uuid, generation, sequence, path);
          });

  public record Node(Vec3 point, PortalLinkId crossing) {}

  @Override
  public Type<CarryInputPayload> type() {
    return TYPE;
  }
}
