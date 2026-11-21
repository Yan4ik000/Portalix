package dev.portalix.test;

import static dev.portalix.test.ServerRegressionSuite.check;

import dev.portalix.compat.minecraft.ServerPlayerMotion;
import dev.portalix.network.portal.PlayerPortalMovePayload;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalViewLimits;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.traversal.PortalPassagePose;
import dev.portalix.server.portal.PlayerPortalMoveState;
import dev.portalix.server.portal.PlayerPortalMovement;
import dev.portalix.server.portal.PortalClosureResolver;
import dev.portalix.test.mixin.ServerCommonPacketListenerImplAccessor;
import dev.portalix.test.mixin.ServerGamePacketListenerImplAccessor;
import dev.portalix.world.chunk.ChunkDeliveryAccess;
import io.netty.util.ReferenceCountUtil;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

final class MovementPacketRegression {
  private MovementPacketRegression() {}

  static void checkReplayCorrection(RegressionWorld world) {
    var entrance = world.endpoint(Direction.NORTH, new Vec3(144, 108, 144));
    var exit = world.endpoint(Direction.EAST, new Vec3(192, 108, 144));
    world.clear(entrance.center().add(entrance.normal().scale(6)), 4);
    world.clear(exit.center().add(exit.normal().scale(4)), 4);
    world.surface(entrance, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    world.surface(exit, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    var link = world.link(entrance, exit);
    world.publish(link);
    try (var participant = new RegressionGameTest.Participant(world.level, "PortalixReplay")) {
      participant.connect(world.configureConnection);
      var player = participant.player;
      var connection = player.connection;
      connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
      checkRelativeTeleport(participant);
      player.setGameMode(GameType.SURVIVAL);
      PlayerPortalVisibility.of(player).ready(link);
      connection.tick();
      participant.channel.flushOutbound();
      participant.channel.runPendingTasks();
      var probe = participant.channel.outboundMessages().stream()
          .filter(ClientboundPingPacket.class::isInstance).map(ClientboundPingPacket.class::cast)
          .findFirst().orElseThrow(() -> new AssertionError("The controller of a ready portal did not get a clock probe"));
      Vec3 before = entrance.center().add(entrance.normal().scale(4))
          .subtract(0, player.getBbHeight() / 2, 0);
      Vec3 movement = entrance.normal().scale(-7);
      Vec3 after = link.transform().point(before.add(movement));
      world.clear(after.add(exit.normal().scale(9)), 10);
      var fire = BlockPos.containing(before.add(entrance.normal().scale(-2)));
      world.block(fire.below(), Blocks.STONE.defaultBlockState());
      world.block(fire, Blocks.FIRE.defaultBlockState());
      for (int step = 0; step < 3; step++) {
        boolean nativeFirst = step == 0;
        boolean wrongPose = step == 1;
        Vec3 start = nativeFirst ? before.add(entrance.normal().scale(4)) : before;
        connection.teleport(start.x, start.y, start.z, 0, 0);
        acceptTeleport(player);
        connection.resetPosition();
        player.setRemainingFireTicks(0);
        player.setOnGround(false);
        if (nativeFirst) {
          connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(before, false, false));
          check(player.position().equals(before) && !ServerPlayerMotion.awaitingTeleport(player),
              "The normal approach to the entrance was not accepted");
        }
        long crossings = EntityPortalState.of(player).crossingSequence();
        PlayerPortalMovement.receive(player, new PlayerPortalMovePayload(step + 1,
            player.getId(), player.getUUID(), MoverType.SELF, before, movement, movement,
            false, after, 1, 0, 0, 0, 0, true, wrongPose ? Pose.CROUCHING : Pose.STANDING, 1));
        check(ServerPlayerMotion.awaitingTeleport(player) == (nativeFirst || wrongPose),
            "Unexpected correction for replay after native approach=" + nativeFirst + ", wrong pose=" + wrongPose);
        check(EntityPortalState.of(player).crossingSequence() == crossings + (nativeFirst ? 0 : 1)
                && player.position().distanceToSqr(nativeFirst ? before : after) < 1e-10,
            "The server replay did not reach its authoritative destination");
        if (!nativeFirst && !wrongPose) {
          connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(
              after.add(exit.normal().scale(4)), false, false));
          check(ServerPlayerMotion.awaitingTeleport(player) && player.position().distanceToSqr(after) < 1e-10,
              "A native movement after a replay got a second path budget");
        }
        player.doTick();
        check(player.isOnFire() != nativeFirst,
            "Fire along the authoritative path disagreed with native approach=" + nativeFirst + ", wrong pose=" + wrongPose);
        connection.handleClientTickEnd(ServerboundClientTickEndPacket.INSTANCE);
      }
      acceptTeleport(player);
      var transit = PlayerPortalMoveState.of(player);
      transit.acknowledgeClock(probe.getId());
      connection.tick();
      check(transit.continuesMovement(player), "A clock probe sent before replay retired its native continuation");
      int serverTick = world.level.getServer().getTickCount();
      for (int step = 1; step <= 6; step++) {
        Vec3 target = after.add(exit.normal().scale(2 * step));
        connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(target, false, false));
        check(world.level.getServer().getTickCount() == serverTick
                && !ServerPlayerMotion.awaitingTeleport(player) && player.position().distanceToSqr(target) < 1e-10,
            "An ordinary packet in a delayed portal continuation was corrected: step=" + step);
        connection.handleClientTickEnd(ServerboundClientTickEndPacket.INSTANCE);
      }
      transit.acknowledgeKeepAlive(Util.getMillis() + 60_000);
      check(transit.continuesMovement(player), "Movement switched to vanilla before its baseline was refreshed");
      connection.tick();
      check(!transit.continuesMovement(player), "The ordered clock response did not retire the portal continuation");
      portalMount(world, participant);
    } finally {
      world.reset();
    }
  }

  private static void checkRelativeTeleport(RegressionGameTest.Participant participant) {
    var player = participant.player;
    var connection = player.connection;
    var tracking = PlayerPortalVisibility.of(player);
    for (double dy : new double[] {8, 0}) {
      tracking.portalCrossed(player.level().getGameTime(), PortalViewLimits.DEFAULT_PREVIEW_RADIUS);
      Vec3 expected = player.position().add(0, dy, 0);
      var change = new PositionMoveRotation(new Vec3(0, dy, 0), new Vec3(.25, .125, 0), 4, -2);
      connection.teleport(change, Relative.ALL);
      participant.channel.flushOutbound();
      participant.channel.runPendingTasks();
      var teleport = participant.channel.outboundMessages().stream()
          .filter(ClientboundPlayerPositionPacket.class::isInstance)
          .map(ClientboundPlayerPositionPacket.class::cast)
          .reduce((first, second) -> second).orElseThrow();
      check(teleport.relatives().equals(Relative.ALL)
              && teleport.change().equals(change)
              && player.position().equals(expected),
          "A relative teleport sent inconsistent coordinates, dy=" + dy);
      check(ServerPlayerMotion.awaitingTeleport(player) && !tracking.delayingLoading(player.level().getGameTime()),
          "An unbundled relative teleport did not reset observation and await confirmation, dy=" + dy);
      acceptTeleport(player);
    }
  }

  static void checkOrderedPongPurposes(RegressionWorld world) {
    try (var participant = new RegressionGameTest.Participant(world.level, "PortalixOrderedPong")) {
      participant.connect(world.configureConnection);
      var player = participant.player;
      var connection = player.connection;
      player.snapTo(152.5, 104, 152.5, 0, 0);
      world.level.getChunkSource().move(player);
      var sender = connection.chunkSender;
      var delivery = (ChunkDeliveryAccess) sender;
      sender.sendNextChunks(player);
      drainChunkRefresh(participant);
      var chunk = world.level.getChunk(9, 9);
      long key = chunk.getPos().pack();
      long time = Util.getMillis();
      var movement = PlayerPortalMoveState.of(player);
      for (boolean clockFirst : new boolean[] {true, false}) {
        var probe = clockFirst ? movement.clockProbe(time) : null;
        delivery.portalix$invalidate(player, key);
        sender.markChunkPendingToSend(chunk);
        sender.sendNextChunks(player);
        var refresh = drainChunkRefresh(participant);
        if (!clockFirst) probe = movement.clockProbe(time + 1000);
        check(probe != null && probe.getId() < 0 && refresh != null && refresh.getId() > 0,
            "Portal clock and chunk refresh did not issue distinct Ping IDs, clockFirst=" + clockFirst);
        check(delivery.portalix$delivered(world.level, key) && !delivery.portalix$received(world.level, key),
            "The refreshed chunk became usable before the installation Pong");
        if (clockFirst) {
          connection.handlePong(new ServerboundPongPacket(probe.getId()));
          check(!movement.isClockProbe(probe.getId()) && !delivery.portalix$received(world.level, key),
              "The clock Pong confirmed an independent chunk refresh");
          connection.handlePong(new ServerboundPongPacket(refresh.getId()));
        } else {
          connection.handlePong(new ServerboundPongPacket(refresh.getId()));
          check(delivery.portalix$received(world.level, key) && movement.isClockProbe(probe.getId()),
              "The chunk refresh Pong acknowledged an independent movement clock");
          connection.handlePong(new ServerboundPongPacket(probe.getId()));
        }
        check(delivery.portalix$received(world.level, key) && !movement.isClockProbe(probe.getId()),
            "An ordered Pong failed to acknowledge its own operation");
      }
    }
  }

  private static ClientboundPingPacket drainChunkRefresh(RegressionGameTest.Participant participant) {
    participant.channel.flushOutbound();
    participant.channel.runPendingTasks();
    ClientboundPingPacket refresh = null;
    Object packet;
    while ((packet = participant.channel.readOutbound()) != null) {
      if (packet instanceof ClientboundChunkBatchFinishedPacket)
        participant.player.connection.chunkSender.onChunkBatchReceivedByClient(16);
      if (packet instanceof ClientboundPingPacket ping) refresh = ping;
      ReferenceCountUtil.release(packet);
    }
    return refresh;
  }

  private static void portalMount(RegressionWorld world, RegressionGameTest.Participant participant) {
    var player = participant.player;
    var boat = world.add(EntityTypes.OAK_BOAT.create(world.level, EntitySpawnReason.COMMAND));
    boat.setPos(player.position());
    participant.channel.flushOutbound();
    participant.channel.runPendingTasks();
    var outbound = participant.channel.outboundMessages();
    int previousPackets = outbound.size();
    check(player.startRiding(boat, true, false), "The portal mount fixture could not board the boat");
    try {
      participant.channel.flushOutbound();
      participant.channel.runPendingTasks();
      var sent = outbound.stream().skip(previousPackets).toList();
      check(sent.stream().noneMatch(packet -> packet instanceof ClientboundPlayerPositionPacket
              || packet instanceof ClientboundSetPassengersPacket),
          "The portal mount sent its position or passengers in a separate packet");
      var bundle = sent.stream().filter(ClientboundBundlePacket.class::isInstance)
          .map(ClientboundBundlePacket.class::cast).findFirst()
          .orElseThrow(() -> new AssertionError("Missing portal mount bundle"));
      var packets = bundle.subPackets().iterator();
      check(packets.hasNext() && packets.next() instanceof ClientboundPlayerPositionPacket teleport
              && teleport.id() == ((ServerGamePacketListenerImplAccessor) player.connection).portalix$teleportId()
              && packets.hasNext() && packets.next() instanceof ClientboundSetPassengersPacket passengers
              && passengers.getVehicle() == boat.getId() && !packets.hasNext(),
          "The portal mount did not bundle its native position before its passenger update");
      check(ServerPlayerMotion.awaitingTeleport(player), "The native mount lost its position acknowledgement");
      acceptTeleport(player);
      check(player.getVehicle() == boat && !ServerPlayerMotion.awaitingTeleport(player),
          "Acknowledging the bundled position changed the player's mount");
    } finally {
      player.stopRiding();
    }
  }

  static void checkVanillaMovement(RegressionWorld world) {
    var player = world.player;
    var connection = player.connection;
    var transit = PlayerPortalMoveState.of(player);
    Vec3 start = new Vec3(144.5, 120, 144.5);
    world.clear(start, 3);
    connection.teleport(start.x, start.y, start.z, 0, 0);
    acceptTeleport(player);
    connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
    transit.acknowledgeKeepAlive(Util.getMillis() + 60_000);
    var outbound = world.channel.outboundMessages();
    int probes = (int) outbound.stream().filter(ClientboundPingPacket.class::isInstance).count();
    connection.tick();
    world.channel.flushOutbound();
    world.channel.runPendingTasks();
    check(outbound.stream().filter(ClientboundPingPacket.class::isInstance).count() == probes,
        "A player without portals received a Portalix clock probe");
    Vec3 next = start.add(1, 0, 0);
    connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(next, false, false));
    check(!ServerPlayerMotion.awaitingTeleport(player) && player.position().equals(next),
        "The portal replay clock corrected ordinary player movement");
    connection.handleClientTickEnd(ServerboundClientTickEndPacket.INSTANCE);
    connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(next.add(20, 0, 0), false, false));
    check(ServerPlayerMotion.awaitingTeleport(player) && player.position().equals(next),
        "The native player speed check did not run outside portal replay");
    acceptTeleport(player);
    var boat = world.add(EntityTypes.OAK_BOAT.create(world.level, EntitySpawnReason.COMMAND));
    boat.setPos(start);
    world.channel.flushOutbound();
    world.channel.runPendingTasks();
    int beforeMount = outbound.size();
    check(player.startRiding(boat, true, false), "The native movement fixture could not board the boat");
    try {
      world.channel.flushOutbound();
      world.channel.runPendingTasks();
      var mount = outbound.stream().skip(beforeMount)
          .filter(packet -> packet instanceof ClientboundPlayerPositionPacket
              || packet instanceof ClientboundSetPassengersPacket)
          .toList();
      check(mount.size() == 2 && mount.getFirst() instanceof ClientboundPlayerPositionPacket
              && mount.getLast() instanceof ClientboundSetPassengersPacket,
          "A mount without portals did not retain the vanilla packet order");
      acceptTeleport(player);
      ServerPlayerMotion.rebase(boat);
      connection.handleMoveVehicle(new ServerboundMoveVehiclePacket(
          PositionAndRotation.of(next, 0, 0), false));
      check(boat.position().equals(next), "The portal replay clock corrected ordinary vehicle movement");
      connection.handleMoveVehicle(new ServerboundMoveVehiclePacket(
          PositionAndRotation.of(next.add(20, 0, 0), 0, 0), false));
      check(boat.position().equals(next), "The native vehicle speed check did not run outside portal replay");
    } finally {
      player.stopRiding();
      world.reset();
    }
  }

  static void checkObservationMovement(RegressionWorld world) {
    Vec3 start = new Vec3(144.5, 120, 144.5);
    Vec3 next = start.add(0, 0, 1);
    world.clear(start, 3);
    var eye = start.add(0, world.player.getEyeHeight(), 0);
    var exit = world.endpoint(Direction.SOUTH, new Vec3(1024, eye.y, 1024));
    var approach = world.link(world.endpoint(Direction.NORTH, eye.add(0, 0, 14)), exit);
    var competing = new ArrayList<PortalLink>();
    for (int index = 0; index < PortalViewLimits.REGIONS; index++)
      competing.add(world.link(world.endpoint(Direction.SOUTH, eye.add(6 + index, 0, 0)),
          world.endpoint(Direction.NORTH, exit.center().add((index + 1) * 1024, 0, 0))));
    competing.add(approach);
    PortalClosureResolver.updateLinks(world.level, competing);
    var center = PortalViewLimits.frontColumn(exit);
    long outsideCore = ChunkPos.pack(center.x() + PortalViewLimits.CORE_RADIUS + 1, center.z());
    try (var participant = new RegressionGameTest.Participant(world.level, "PortalixApproach")) {
      participant.connect(world.configureConnection);
      var player = participant.player;
      var connection = player.connection;
      var observation = PlayerPortalVisibility.of(player);
      connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
      for (boolean mounted : new boolean[] {false, true}) {
        connection.teleport(start.x, start.y, start.z, 0, 0);
        acceptTeleport(player);
        var subject = mounted ? world.add(EntityTypes.OAK_BOAT.create(world.level, EntitySpawnReason.COMMAND)) : player;
        subject.setPos(start);
        if (mounted) {
          check(player.startRiding(subject, true, false), "The arrival priority fixture could not board the boat");
          acceptTeleport(player);
        }
        subject.setDeltaMovement(Vec3.ZERO);
        player.setKnownMovement(Vec3.ZERO);
        ServerPlayerMotion.rebase(subject);
        check(!observation.contains(outsideCore), "The distant arrival was already selected against nearer portals");
        if (mounted) connection.handleMoveVehicle(new ServerboundMoveVehiclePacket(
            PositionAndRotation.of(next, 0, 0), false));
        else connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(next, false, false));
        check(subject.position().distanceToSqr(next) < 1e-10
                && player.getKnownMovement().distanceToSqr(next.subtract(start)) < 1e-10,
            "The native approach packet did not arrive, mounted=" + mounted + " pos=" + subject.position()
                + " known movement=" + player.getKnownMovement());
        check(observation.contains(outsideCore) && observation.observers().stream()
                .anyMatch(observer -> observer.exit().equals(exit) && observer.arrival()),
            "An accepted native approach lost the portal terrain priority, mounted=" + mounted);
        connection.handleClientTickEnd(ServerboundClientTickEndPacket.INSTANCE);
      }
    } finally {
      world.reset();
    }
  }

  static void acceptTeleport(ServerPlayer player) {
    var connection = player.connection;
    var access = (ServerGamePacketListenerImplAccessor) connection;
    var position = access.portalix$teleportPosition();
    if (position != null)
      connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(
          access.portalix$teleportId(), position.x, position.y, position.z,
          player.getYRot(), player.getXRot()));
  }

  static void checkKeepAliveOrdering(RegressionWorld world) {
    var connection = world.player.connection;
    var listener = (ServerCommonPacketListenerImplAccessor) connection;
    var movement = PlayerPortalMoveState.of(world.player);
    var processor = world.level.getServer().packetProcessor();
    boolean pending = listener.portalix$keepAlivePending();
    long challenge = listener.portalix$keepAliveChallenge();
    long time = listener.portalix$keepAliveTime();
    int latency = listener.portalix$latency();
    long now = Util.getMillis();
    movement.acknowledgeKeepAlive(now);
    var probe = movement.clockProbe(now + 1000);
    check(probe != null, "The native heartbeat fixture has no clock probe");
    long sentAt = now + 60_000;
    var failure = new AtomicReference<Throwable>();
    var network = new Thread(() -> {
      try {
        connection.handleKeepAlive(new ServerboundKeepAlivePacket(sentAt));
      } catch (Throwable error) {
        failure.set(error);
      }
    }, "portalix-regression-keepalive");
    network.setDaemon(true);
    try {
      listener.portalix$keepAlivePending(true);
      listener.portalix$keepAliveChallenge(sentAt);
      listener.portalix$keepAliveTime(sentAt);
      network.start();
      try {
        network.join(5000);
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Interrupted while checking native heartbeat handling", error);
      }
      check(!network.isAlive(), "Native heartbeat handling waited for the stalled server thread");
      if (failure.get() != null)
        throw new AssertionError("Native heartbeat handling failed on the network thread", failure.get());
      check(!listener.portalix$keepAlivePending(),
          "A valid native heartbeat did not immediately preserve vanilla connection liveness");
      check(movement.isClockProbe(probe.getId()),
          "A native heartbeat applied its clock bound before earlier queued movement");
      processor.processQueuedPackets();
      check(!movement.isClockProbe(probe.getId()),
          "The queued native heartbeat did not retire its superseded movement clock probe");
      for (int tick = 0; tick < 20; tick++) {
        check(movement.beginMovement(sentAt, Vec3.ZERO),
            "The native heartbeat handler rejected the permitted client frame phase");
        movement.endClientTick(sentAt);
      }
      check(!movement.beginMovement(sentAt, Vec3.ZERO),
          "The native heartbeat handler retained stale movement time after its queue barrier");
    } finally {
      network.interrupt();
      processor.processQueuedPackets();
      listener.portalix$keepAlivePending(pending);
      listener.portalix$keepAliveChallenge(challenge);
      listener.portalix$keepAliveTime(time);
      listener.portalix$latency(latency);
    }
  }

  static void checkBatchedMovement(RegressionWorld world, double speed, int steps) {
    var player = world.player;
    var connection = player.connection;
    connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
    acceptTeleport(player);
    var replay = PlayerPortalMoveState.of(player);
    replay.resetForWorld();
    player.setGameMode(GameType.SURVIVAL);
    player.setPose(Pose.STANDING);
    player.setShiftKeyDown(false);
    for (int x = 143; x <= 219; x++)
      for (int z = 143; z <= 145; z++) {
        world.block(new BlockPos(x, 120, z), Blocks.AIR.defaultBlockState());
        world.block(new BlockPos(x, 121, z), Blocks.STONE.defaultBlockState());
      }
    player.setPos(144.5, 120, 144.5);
    PortalPassagePose.set(player, 1f / 3);
    player.setOnGround(false);
    player.setDeltaMovement(speed, 0, 0);
    int serverTick = world.level.getServer().getTickCount();
    for (int step = 0; step < steps; step++) {
      Vec3 before = player.position();
      Vec3 movement = new Vec3(speed, 0, 0);
      float scale = PortalPassagePose.scale(player);
      PlayerPortalMovement.receive(player, new PlayerPortalMovePayload(step + 1,
          player.getId(), player.getUUID(), MoverType.SELF, before, movement, movement,
          false, before.add(movement), scale, 0, 0, 0, 0, true, Pose.STANDING, scale));
      check(world.level.getServer().getTickCount() == serverTick,
          "The replay burst escaped its single server tick");
      check(!ServerPlayerMotion.awaitingTeleport(player)
              && player.position().distanceToSqr(before.add(movement)) < 1e-10,
          "A valid batched client movement was corrected: speed=" + speed + ", step=" + step);
      connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(player.position(), false, false));
      check(!ServerPlayerMotion.awaitingTeleport(player)
              && player.position().distanceToSqr(before.add(movement)) < 1e-10,
          "The native echo duplicated or corrected replayed movement");
      if (step < steps - 1)
        connection.handleClientTickEnd(ServerboundClientTickEndPacket.INSTANCE);
      speed *= .91;
    }
    Vec3 beforeExcess = player.position();
    Vec3 excess = new Vec3(10, 0, 0);
    float scale = PortalPassagePose.scale(player);
    PlayerPortalMovement.receive(player, new PlayerPortalMovePayload(steps + 1,
        player.getId(), player.getUUID(), MoverType.SELF, beforeExcess, excess, excess,
        false, beforeExcess.add(excess), scale, 0, 0, 0, 0, false, Pose.STANDING, scale));
    check(ServerPlayerMotion.awaitingTeleport(player)
            && player.position().distanceToSqr(beforeExcess) < 1e-10,
        "Splitting movement across packets bypassed a client tick's budget");
    connection.handleClientTickEnd(ServerboundClientTickEndPacket.INSTANCE);
    world.reset();
  }
}
