package dev.portalix.network;

import static dev.portalix.test.PortalFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.portalix.item.PortalixVariant;
import dev.portalix.network.carry.*;
import dev.portalix.network.gun.*;
import dev.portalix.network.portal.*;
import dev.portalix.portal.PortalLinkId;
import dev.portalix.portal.traversal.PortalEntityMovement.Crossing;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.joml.Quaternionf;

class PayloadCodecsTest {
  private record Packet<T>(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
    @Override public String toString() { return value.getClass().getSimpleName() + ": " + value; }
  }

  static Stream<Packet<?>> packets() {
    var packets = new ArrayList<Packet<?>>();
    UUID entity = new UUID(Long.MIN_VALUE, Long.MAX_VALUE), owner = new UUID(3, 5);
    Vec3 position = new Vec3(-29_999_999.875, -63.125, 29_999_999.5), velocity = new Vec3(.125, -3.75, 100.5);
    var portals = orientations();
    var link = link(portals.get(0), portals.get(6));
    var reference = PortalLinkId.fromLink(link);
    var crossing = new Crossing(Long.MAX_VALUE, link, position, velocity, new Vec3(1, 2, 3), -179.75f, 89.5f, .375, Vec3.ZERO, 1.62f, 90.25f, -45.125f, .5f);
    long sequence = 128;
    packets.add(new Packet<>(PortalViewReadyPayload.CODEC, new PortalViewReadyPayload(sequence, sequence)));
    packets.add(new Packet<>(ProjectileStatePayload.CODEC, new ProjectileStatePayload(128, entity, sequence, sequence, position, velocity, -179.75f, 89.5f)));
    packets.add(new Packet<>(EntityPortalInitPayload.CODEC, new EntityPortalInitPayload(
        128, entity, sequence, new PositionMoveRotation(position, velocity, -179.75f, 89.5f), true,
        new EntityPortalInitPayload.Rotation(new Quaternionf().rotationXYZ(.31f, -.42f, .53f)))));
    packets.add(new Packet<>(EntityPortalInitPayload.CODEC, new EntityPortalInitPayload(
        128, entity, sequence, new PositionMoveRotation(position, velocity, -179.75f, 89.5f), true,
        new EntityPortalInitPayload.FishingTether(List.of(link, link.inverse()), sequence, List.of(link)))));
    for (boolean flag : new boolean[] {false, true}) {
      packets.add(new Packet<>(CarryGrabPayload.CODEC, new CarryGrabPayload(128, entity, sequence, sequence, 1024, owner, sequence, position, velocity, flag ? List.of(reference, reference.inverse()) : List.of())));
      packets.add(new Packet<>(CarryInputPayload.CODEC, new CarryInputPayload(128, entity, sequence, sequence, flag ? List.of(new CarryInputPayload.Node(position, null), new CarryInputPayload.Node(velocity, reference)) : List.of(new CarryInputPayload.Node(position, null)))));
      packets.add(new Packet<>(CarryReleasePayload.CODEC, new CarryReleasePayload(128, entity, sequence, position, velocity, flag, sequence, sequence)));
      packets.add(new Packet<>(CarryStatePayload.CODEC, new CarryStatePayload(128, entity, sequence, sequence, sequence, position, velocity, flag, !flag)));
      packets.add(new Packet<>(PlayerPortalMoveAckPayload.CODEC, new PlayerPortalMoveAckPayload(sequence, flag, .5f, List.of(link))));
      packets.add(new Packet<>(GunStatePayload.CODEC, new GunStatePayload(1024, owner, flag ? 128 : -1, entity, 127, flag ? 3 : 0, flag)));
    }
    for (int mask = 0; mask < 4; mask++) {
      packets.add(new Packet<>(PortalStatePayload.CODEC, new PortalStatePayload(owner, sequence, sequence,
          (mask & 1) != 0 ? link.entrance() : null, (mask & 2) != 0 ? link.exit() : null, true)));
    }
    for (PortalixVariant variant : PortalixVariant.values()) {
      packets.add(new Packet<>(GunShotPayload.CODEC, new GunShotPayload(128, position, velocity, new Vec3(0, 1, 0), false, new Vec3(0, 0, -1), sequence, 128, sequence, variant)));
      packets.add(new Packet<>(PortalImpactPayload.CODEC, new PortalImpactPayload(sequence, position, velocity, new Vec3(0, 1, 0), false, true, sequence, variant)));
    }
    packets.add(new Packet<>(ProjectilePortalMovePayload.CODEC, new ProjectilePortalMovePayload(128, entity)));
    for (GunCommand command : GunCommand.values()) packets.add(new Packet<>(GunCommandPayload.CODEC, new GunCommandPayload(command)));
    for (GunEventKind event : GunEventKind.values()) packets.add(new Packet<>(GunEventPayload.CODEC, new GunEventPayload(Integer.MAX_VALUE, event)));
    for (Pose pose : Pose.values()) {
      packets.add(new Packet<>(PlayerPortalMovePayload.CODEC, new PlayerPortalMovePayload(Long.MAX_VALUE, 128, entity, MoverType.SELF, position, velocity, Vec3.ZERO, true, pose, 1, false, position.add(velocity), .5f, -180, 90, 37.5f, -45, pose, .75f)));
    }
    for (MoverType mover : MoverType.values()) {
      packets.add(new Packet<>(PlayerPortalMovePayload.CODEC, new PlayerPortalMovePayload(Long.MAX_VALUE, 128, entity, mover, position, velocity, Vec3.ZERO, true, Pose.STANDING, 1, false, position.add(velocity), .5f, -180, 90, 37.5f, -45, Pose.STANDING, .75f)));
    }
    packets.add(new Packet<>(EntityPortalCrossingPayload.CODEC, new EntityPortalCrossingPayload(128, entity, List.of(crossing, crossing), position, velocity, -179.75f, 89.5f, 37.25f, -72.5f, .5f)));
    packets.add(new Packet<>(CarryInputPayload.CODEC, new CarryInputPayload(128, entity, 1, 2, Collections.nCopies(CarryInputPayload.MAX_POINTS, new CarryInputPayload.Node(position, null)))));
    packets.add(new Packet<>(CarryGrabPayload.CODEC, new CarryGrabPayload(128, entity, 1, 2, 1024, owner, 3, position, velocity, Collections.nCopies(64, reference))));
    return packets.stream();
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("packets")
  <T> void packetRoundTripRetainsEveryFieldAndConsumesExactlyItsBytes(Packet<T> packet) {
    var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    try {
      packet.codec().encode(buffer, packet.value());
      int bytes = buffer.readableBytes();
      packet.codec().encode(buffer, packet.value());
      assertEquals(packet.value(), packet.codec().decode(buffer));
      assertEquals(bytes, buffer.readerIndex());
      assertEquals(packet.value(), packet.codec().decode(buffer));
      assertEquals(0, buffer.readableBytes());
    } finally {
      buffer.release();
    }
  }

  @Test
  void malformedCommandsAndOversizedRoutesFailAtTheProtocolBoundary() {
    int unknownCommand = GunCommand.values().length;
    for (int invalid : new int[] {-1, unknownCommand, Math.max(127, unknownCommand), Integer.MAX_VALUE}) {
      var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
      try {
        buffer.writeVarInt(invalid);
        assertThrows(RuntimeException.class, () -> GunCommandPayload.CODEC.decode(buffer));
      } finally {
        buffer.release();
      }
    }
    for (int size : new int[] {-1, 0, CarryInputPayload.MAX_POINTS + 1, Integer.MAX_VALUE}) {
      var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
      try {
        buffer.writeVarInt(1);
        buffer.writeUUID(new UUID(2, 3));
        buffer.writeVarLong(4);
        buffer.writeVarLong(5);
        buffer.writeVarInt(size);
        assertThrows(IllegalArgumentException.class, () -> CarryInputPayload.CODEC.decode(buffer));
      } finally {
        buffer.release();
      }
    }
    assertThrows(IllegalArgumentException.class, () -> new GunStatePayload(-1, new UUID(1, 2), -1, null, 0, 0, false));
    assertThrows(IllegalArgumentException.class, () -> new GunStatePayload(1, new UUID(1, 2), -1, null, 0, 4, false));
    assertThrows(IllegalArgumentException.class, () -> new CarryInputPayload(1, new UUID(1, 2), 1, 1, List.of()));
  }

  @Test
  void malformedEntityInitializationIsRejected() {
    var rotations = new Quaternionf[] {
        new Quaternionf(0, 0, 0, 0), new Quaternionf(Float.NaN, 0, 0, 1),
        new Quaternionf(0, Float.POSITIVE_INFINITY, 0, 1), new Quaternionf(0, 0, 0, 2)
    };
    for (int index = 0; index <= rotations.length; index++) {
      var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
      try {
        buffer.writeVarInt(128);
        buffer.writeUUID(new UUID(1, 2));
        buffer.writeVarLong(index == rotations.length ? -1 : 0);
        PositionMoveRotation.STREAM_CODEC.encode(buffer, new PositionMoveRotation(
            new Vec3(1, -64, 300), new Vec3(.125, -1.5, 3), 91.5f, -23.25f));
        buffer.writeBoolean(true);
        buffer.writeByte(0);
        buffer.writeQuaternion(index == rotations.length ? new Quaternionf() : rotations[index]);
        assertThrows(IllegalArgumentException.class, () -> EntityPortalInitPayload.CODEC.decode(buffer));
      } finally {
        buffer.release();
      }
    }
  }
}
