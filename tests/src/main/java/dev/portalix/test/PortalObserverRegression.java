package dev.portalix.test;

import static dev.portalix.test.ChunkWatchTestHooks.Event.SENT;
import static dev.portalix.test.ChunkWatchTestHooks.Event.UNWATCH;
import static dev.portalix.test.ChunkWatchTestHooks.Event.WATCH;
import static dev.portalix.test.ServerRegressionSuite.check;

import dev.portalix.PortalixMod;
import dev.portalix.mixin.world.tracking.ChunkMapAccessor;
import dev.portalix.network.portal.PortalViewDemandPayload;
import dev.portalix.platform.ModPresence;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalLinkId;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.PortalViewLimits;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.server.PortalEntityTracking;
import dev.portalix.server.PortalServerEvents;
import dev.portalix.server.portal.PortalClosureResolver;
import dev.portalix.world.chunk.ChunkDeliveryAccess;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

final class PortalObserverRegression implements AutoCloseable {
  private static final int VIEW_DISTANCE = 10;
  private static final ChunkPos SHARED_CENTER = new ChunkPos(9, 9);
  private static final ChunkPos REMOTE_CENTER = new ChunkPos(39, 9);
  private static final long OUTER_COLUMN = ChunkPos.pack(SHARED_CENTER.x() - VIEW_DISTANCE, SHARED_CENTER.z());

  private final RegressionWorld world;
  private final RegressionGameTest.Participant peer;
  private final int originalDistance;
  private final ClientInformation originalOptions;
  private final PortalLink crossing;
  private final PortalLink respawnLink;
  private final List<PortalLink> originalLinks;
  private Phase phase = Phase.WAIT_FOR_RESPAWN_TICKET;
  private long checkRespawnAt;
  private long resumeLoadingAt;
  private long disconnectedPeerColumn;
  private final long startTick;
  private List<PortalLink> cameraPath = List.of();
  private ItemEntity cameraTarget;
  private ChunkWatchTestHooks reentry;

  PortalObserverRegression(RegressionWorld world) {
    this.world = world;
    var chunks = world.level.getChunkSource();
    originalDistance = ((ChunkMapAccessor) chunks.chunkMap).portalix$serverViewDistance();
    originalOptions = world.player.clientInformation();
    peer = new RegressionGameTest.Participant(world.level, "PortalixPeer");
    var center = centerPosition(SHARED_CENTER);
    crossing = world.link(world.endpoint(Direction.NORTH, center), world.endpoint(Direction.SOUTH, center.add(1, 0, 1)));
    var respawn = centerPosition(REMOTE_CENTER);
    respawnLink = world.link(world.endpoint(Direction.NORTH, respawn.add(0, 1, 3)),
        world.endpoint(Direction.SOUTH, respawn.add(2048, 1, 0)));
    originalLinks = PortalTopology.links(world.level);
    startTick = world.level.getGameTime();
    try {
      peer.connect(world.configureConnection);
      world.player.setNoGravity(true);
      peer.player.setNoGravity(true);
      setViewDistance(world.player, VIEW_DISTANCE);
      setViewDistance(peer.player, VIEW_DISTANCE);
      moveToChunk(world.player, SHARED_CENTER);
      moveToChunk(peer.player, REMOTE_CENTER);
      chunks.setViewDistance(VIEW_DISTANCE);
      var links = new ArrayList<>(originalLinks);
      links.add(respawnLink);
      links.add(respawnLink.inverse());
      PortalClosureResolver.updateLinks(world.level, links);
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  boolean tick() {
    long tick = world.level.getGameTime();
    if (tick - startTick >= 800) throw new AssertionError("Two-observer tracking timed out at phase " + phase
        + " primary=" + world.player.getChunkTrackingView() + " peer=" + peer.player.getChunkTrackingView()
        + " edge=" + hasOuterTicket());
    acknowledgeChunkBatches(world.player, world.channel);
    if (phase != Phase.VERIFY_DISCONNECT) acknowledgeChunkBatches(peer.player, peer.channel);
    switch (phase) {
      case WAIT_FOR_RESPAWN_TICKET -> {
        long dest = PortalViewLimits.frontColumn(respawnLink.exit().endpoint()).pack();
        if (!PlayerPortalVisibility.of(peer.player).simulationChunks().contains(dest) || !simulationTicket(dest)) return false;
        check(!PlayerPortalVisibility.of(world.player).simulationChunks().contains(dest),
            "The other observer also supplies the remote respawn ticket");
        var original = peer.player;
        var connection = original.connection;
        var respawnPos = BlockPos.containing(centerPosition(REMOTE_CENTER));
        var spawn = LevelData.RespawnData.of(world.level.dimension(), respawnPos, 0, 0);
        original.setRespawnPosition(new ServerPlayer.RespawnConfig(spawn, true), false);
        original.setHealth(0);
        connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        peer.player = connection.player;
        check(peer.player != original && peer.player.getUUID().equals(original.getUUID())
                && peer.player.level() == world.level && peer.player.chunkPosition().equals(REMOTE_CENTER),
            "Vanilla did not replace the observer at the same respawn point");
        peer.player.setNoGravity(true);
        checkRespawnAt = tick + 2;
        phase = Phase.VERIFY_RESPAWN;
      }
      case VERIFY_RESPAWN -> {
        if (tick < checkRespawnAt) return false;
        if (cameraTarget == null) {
          long dest = PortalViewLimits.frontColumn(respawnLink.exit().endpoint()).pack();
          check(PlayerPortalVisibility.of(peer.player).simulationChunks().contains(dest) && simulationTicket(dest),
              "Same-world respawn lost the simulation region at an unchanged portal exit");
          prepareCameraTracking();
        }
        if (!verifyCameraTracking()) return false;
        if (reentry == null) finishCameraTracking();
        else phase = Phase.VERIFY_REENTRY;
      }
      case VERIFY_REENTRY -> {
        check(reentry.events().equals(List.of(UNWATCH, WATCH, SENT)),
            "Reopening a retained chunk did not restore its watch order: " + reentry.events());
        reentry.close();
        reentry = null;
        verifyDeliveredRemoteUnWatch();
        finishCameraTracking();
      }
      case SHARE_VIEW -> {
        if (!hasView(world.player, SHARED_CENTER, VIEW_DISTANCE) || !hasView(peer.player, SHARED_CENTER, VIEW_DISTANCE)
            || !hasOuterTicket()) return false;
        PortalServerEvents.cameraCrossed(world.player, crossing);
        resumeLoadingAt = PlayerPortalVisibility.of(world.player).resumeLoadingAt();
        phase = Phase.SHRINK_VIEW;
      }
      case SHRINK_VIEW -> {
        check(tick < resumeLoadingAt, "The shared-center view failed to enter its delay");
        if (!hasView(world.player, SHARED_CENTER, 8)) return false;
        check(hasView(peer.player, SHARED_CENTER, VIEW_DISTANCE) && hasOuterTicket(),
            "A crossing reduced another observer's shared center");
        moveToChunk(peer.player, REMOTE_CENTER);
        setViewDistance(world.player, 4);
        phase = Phase.RELEASE_OUTER_TICKET;
      }
      case RELEASE_OUTER_TICKET -> {
        check(tick < resumeLoadingAt, "The departing observer kept an unshared native outer ticket");
        if (!hasView(world.player, SHARED_CENTER, 4) || !hasView(peer.player, REMOTE_CENTER, VIEW_DISTANCE)
            || hasOuterTicket()) return false;
        setViewDistance(world.player, VIEW_DISTANCE);
        moveToChunk(peer.player, SHARED_CENTER);
        phase = Phase.VERIFY_DIMENSION_CHANGE;
      }
      case VERIFY_DIMENSION_CHANGE -> {
        if (tick >= resumeLoadingAt - 2) {
          PortalServerEvents.cameraCrossed(world.player, crossing.inverse());
          resumeLoadingAt = PlayerPortalVisibility.of(world.player).resumeLoadingAt();
        }
        if (!hasView(world.player, SHARED_CENTER, 8) || !hasView(peer.player, SHARED_CENTER, VIEW_DISTANCE)
            || !hasOuterTicket()) return false;
        PortalServerEvents.cameraCrossed(peer.player, crossing);
        long retainedColumn = heldColumn(peer.player);
        var nether = world.level.getServer().getLevel(Level.NETHER);
        try (var trace = traceRemoteWatch(new ChunkPos(SHARED_CENTER.x(), 48))) {
          check(nether != null && peer.player.teleportTo(nether, 8, 90, 8, Set.of(), 0, 0, true),
              "The second observer could not enter the Nether");
          verifyRemoteUnWatch(trace);
        }
        check(!PlayerPortalVisibility.of(peer.player).delayingLoading(nether.getGameTime())
                && !PortalServerEvents.retainsTerrain(peer.player, retainedColumn),
            "Changing dimensions preserved the old delay or held columns");
        var returnPos = centerPosition(SHARED_CENTER);
        check(peer.player.teleportTo(world.level, returnPos.x, returnPos.y, returnPos.z, Set.of(), 0, 0, true),
            "The second observer could not return");
        phase = Phase.DISCONNECT_PEER;
      }
      case DISCONNECT_PEER -> {
        var delivery = (ChunkDeliveryAccess) peer.player.connection.chunkSender;
        if (!hasView(peer.player, SHARED_CENTER, VIEW_DISTANCE)
            || !delivery.portalix$delivered(world.level, SHARED_CENTER.pack())) return false;
        PortalServerEvents.cameraCrossed(peer.player, crossing);
        disconnectedPeerColumn = heldColumn(peer.player);
        try (var trace = traceRemoteWatch(new ChunkPos(SHARED_CENTER.x(), 80))) {
          peer.close();
          verifyRemoteUnWatch(trace);
        }
        phase = Phase.VERIFY_DISCONNECT;
      }
      case VERIFY_DISCONNECT -> {
        if (PortalServerEvents.retainsTerrain(peer.player, disconnectedPeerColumn)) return false;
        if (tick < resumeLoadingAt) {
          check(hasView(world.player, SHARED_CENTER, 8), "Disconnecting a different observer released the loading delay");
          return false;
        }
        return hasView(world.player, SHARED_CENTER, VIEW_DISTANCE) && hasOuterTicket();
      }
    }
    return false;
  }

  private void prepareCameraTracking() {
    var eye = world.player.getEyePosition();
    var firstExit = new Vec3(eye.x, eye.y, 768);
    var secondExit = new Vec3(eye.x, eye.y, 1280);
    var target = peer.player.getEyePosition();
    cameraPath = List.of(
        cameraLink(eye.add(0, 0, 1), firstExit),
        cameraLink(firstExit.add(0, 0, 1), secondExit),
        cameraLink(secondExit.add(0, 0, 1), target));
    var links = new ArrayList<>(originalLinks);
    for (var link : cameraPath) {
      links.add(link);
      links.add(link.inverse());
      var center = PortalViewLimits.frontColumn(link.exit().endpoint());
      for (int x = center.x() - 1; x <= center.x() + 1; x++)
        for (int z = center.z() - 1; z <= center.z() + 1; z++) world.level.getChunk(x, z);
      PlayerPortalVisibility.of(world.player).ready(link);
    }
    PortalClosureResolver.updateLinks(world.level, links);
    cameraTarget = world.item(target.add(0, 0, 2));
    var pos = cameraTarget.chunkPosition();
    ChunkMapAccessor.portalix$markChunkPendingToSend(world.player, world.level.getChunk(pos.x(), pos.z()));
  }

  private PortalLink cameraLink(Vec3 entrance, Vec3 exit) {
    return world.link(world.endpoint(Direction.NORTH, entrance.add(.5, 0, 0)),
        world.endpoint(Direction.SOUTH, exit.subtract(.5, 0, 0)));
  }

  private boolean verifyCameraTracking() {
    var player = world.player;
    var map = (ChunkMapAccessor) world.level.getChunkSource().chunkMap;
    var tracked = (PortalEntityTracking.Tracker) map.portalix$entityTrackers().get(cameraTarget.getId());
    var delivery = (ChunkDeliveryAccess) player.connection.chunkSender;
    long column = cameraTarget.chunkPosition().pack();
    if (!delivery.portalix$delivered(world.level, column)
        || tracked == null || !tracked.portalix$hasObserver(peer.player)) return false;

    PortalServerEvents.tick(world.level.getServer());
    var visibility = PlayerPortalVisibility.of(player);
    check(!tracked.portalix$hasObserver(player), "Camera-only entity was visible from the body");
    var last = cameraPath.getLast();
    check(visibility.isReady(last), "The camera tracking test requires an already confirmed exit");
    var ids = cameraPath.stream().map(PortalLinkId::fromLink).toList();
    PortalServerEvents.requestViews(player, new PortalViewDemandPayload(ids, List.of()));
    PortalServerEvents.tick(world.level.getServer());
    check(visibility.eyes().stream().anyMatch(eye -> eye.distanceToSqr(cameraTarget.position()) < 64),
        "The third-person camera frame did not publish its remote eye");
    check(tracked.portalix$hasObserver(player), "The stationary camera observer missed the already delivered entity");
    check(tracked.portalix$hasObserver(peer.player), "Entering a camera frame changed another observer's subscription");

    if (ModPresence.INSTANCE.isLoaded("neoforge"))
      reentry = ChunkWatchTestHooks.trace(player, cameraTarget.chunkPosition());
    PortalServerEvents.requestViews(player, new PortalViewDemandPayload(List.of(), List.of()));
    PortalServerEvents.tick(world.level.getServer());
    check(delivery.portalix$delivered(world.level, column) && visibility.isReady(last),
        "The entity disappeared after a terrain delivery change");
    check(!tracked.portalix$hasObserver(player), "The stationary body kept subscribing to the camera-only entity");
    check(tracked.portalix$hasObserver(peer.player), "Leaving a camera frame removed another observer's subscription");

    if (reentry != null) {
      check(reentry.events().equals(List.of(UNWATCH)),
          "Retiring a delivered remote chunk missed its UnWatch: " + reentry.events());
      check(delivery.portalix$retain(player, column), "The retirement released the chunk before it could be retained");
      check(delivery.portalix$delivered(world.level, column),
          "Reopening a fresh retained chunk queued another snapshot");
    }
    return true;
  }

  private void finishCameraTracking() {
    cameraTarget.discard();
    verifyCameraDistanceLimit(world.player, PlayerPortalVisibility.of(world.player));
    PortalClosureResolver.updateLinks(world.level, originalLinks);
    moveToChunk(peer.player, SHARED_CENTER);
    phase = Phase.SHARE_VIEW;
  }

  private ChunkWatchTestHooks traceRemoteWatch(ChunkPos pos) {
    if (!ModPresence.INSTANCE.isLoaded("neoforge")) return null;
    var player = peer.player;
    var delivery = (ChunkDeliveryAccess) player.connection.chunkSender;
    check(!player.getChunkTrackingView().contains(pos) && !delivery.portalix$subscribed(world.level, pos.pack()),
        "The teardown fixture was already watched");
    var chunk = world.level.getChunk(pos.x(), pos.z());
    var trace = ChunkWatchTestHooks.trace(player, pos);
    ChunkMapAccessor.portalix$markChunkPendingToSend(player, chunk);
    return trace;
  }

  private void verifyDeliveredRemoteUnWatch() {
    var player = world.player;
    var pos = cameraTarget.chunkPosition();
    var delivery = (ChunkDeliveryAccess) player.connection.chunkSender;
    check(!player.getChunkTrackingView().contains(pos) && delivery.portalix$delivered(world.level, pos.pack()),
        "The reentry fixture did not deliver a remote-only watch");
    var returnPos = player.position();
    var nether = world.level.getServer().getLevel(Level.NETHER);
    try (var trace = ChunkWatchTestHooks.trace(player, pos)) {
      check(nether != null && player.teleportTo(nether, 8, 90, 8, Set.of(), 0, 0, true),
          "The observer of a delivered remote chunk could not enter the Nether");
      check(trace.events().equals(List.of(UNWATCH)),
          "Dimension change missed the delivered remote-only UnWatch: " + trace.events());
    }
    check(player.teleportTo(world.level, returnPos.x, returnPos.y, returnPos.z, Set.of(), 0, 0, true),
        "The observer of a delivered remote chunk could not return");
  }

  private static void verifyRemoteUnWatch(ChunkWatchTestHooks trace) {
    if (trace != null)
      check(trace.events().equals(List.of(WATCH, UNWATCH)),
          "Leaving the level did not end the remote-only subscription in its original dimension: " + trace.events());
  }

  private void verifyCameraDistanceLimit(ServerPlayer player, PlayerPortalVisibility visibility) {
    player.setDeltaMovement(Vec3.ZERO);
    Vec3 eye = player.getEyePosition();
    Vec3 target = peer.player.getEyePosition();
    var extendedCamera = cameraLink(eye.add(0, 0, 12), target.add(2, 0, 0));
    var outOfReachCamera = cameraLink(eye.add(0, 0, 40), target.add(4, 0, 0));
    var links = new ArrayList<>(originalLinks);
    links.addAll(List.of(extendedCamera, extendedCamera.inverse(), outOfReachCamera, outOfReachCamera.inverse()));
    PortalClosureResolver.updateLinks(world.level, links);
    visibility.ready(extendedCamera);
    visibility.ready(outOfReachCamera);

    boolean neoForge = ModPresence.INSTANCE.isLoaded("neoforge");
    PortalServerEvents.requestViews(player, new PortalViewDemandPayload(List.of(PortalLinkId.fromLink(extendedCamera)), List.of()));
    PortalServerEvents.tick(world.level.getServer());
    check(visibility.observers().stream().anyMatch(observer -> observer.arrival()
        && observer.exit().equals(extendedCamera.exit().endpoint())) == neoForge,
        "Detached camera validation disagreed with the loader's distance limit");
    PortalServerEvents.requestViews(player, new PortalViewDemandPayload(List.of(), List.of()));
    PortalServerEvents.requestViews(player, new PortalViewDemandPayload(List.of(PortalLinkId.fromLink(outOfReachCamera)), List.of()));
    PortalServerEvents.tick(world.level.getServer());
    check(visibility.observers().stream().noneMatch(observer -> observer.arrival()
        && observer.exit().equals(outOfReachCamera.exit().endpoint())),
        "Detached camera validation accepted an entrance beyond the scaled maximum");
    PortalServerEvents.requestViews(player, new PortalViewDemandPayload(List.of(), List.of()));
  }

  private static long heldColumn(ServerPlayer player) {
    var delivery = (ChunkDeliveryAccess) player.connection.chunkSender;
    for (long key : delivery.portalix$presentColumns(player.level()))
      if (PortalServerEvents.retainsTerrain(player, key)) return key;
    throw new AssertionError("The observer did not acquire a delivered warm column");
  }

  private boolean simulationTicket(long column) {
    var type = BuiltInRegistries.TICKET_TYPE.getValue(PortalixMod.id("portal_simulation"));
    return world.level.getChunkSource().getDataStorage().computeIfAbsent(TicketStorage.TYPE)
        .getTickets(column).stream().anyMatch(ticket -> ticket.getType() == type);
  }

  private boolean hasOuterTicket() {
    var manager = world.level.getChunkSource().chunkMap.getDistanceManager();
    return ((PlayerTicketDistanceAccess) manager).portalix$playerTicketDistance(OUTER_COLUMN) <= VIEW_DISTANCE;
  }

  private static boolean hasView(ServerPlayer player, ChunkPos center, int radius) {
    return player.getChunkTrackingView() instanceof ChunkTrackingView.Positioned view
        && view.center().equals(center) && view.viewDistance() == radius;
  }

  private static void acknowledgeChunkBatches(ServerPlayer player, EmbeddedChannel channel) {
    player.connection.chunkSender.sendNextChunks(player);
    channel.flushOutbound();
    channel.runPendingTasks();
    Object packet;
    while ((packet = channel.readOutbound()) != null) {
      if (packet instanceof ClientboundChunkBatchFinishedPacket)
        player.connection.chunkSender.onChunkBatchReceivedByClient(16);
      ReferenceCountUtil.release(packet);
    }
  }

  private static void setViewDistance(ServerPlayer player, int distance) {
    var info = player.clientInformation();
    player.updateOptions(new ClientInformation(info.language(), distance, info.chatVisibility(), info.chatColors(),
        info.modelCustomisation(), info.mainHand(), info.textFilteringEnabled(), info.allowsListing(), info.particleStatus()));
  }

  private static Vec3 centerPosition(ChunkPos chunk) {
    return new Vec3(chunk.x() * 16 + 8.5, 104, chunk.z() * 16 + 8.5);
  }

  private static void moveToChunk(ServerPlayer player, ChunkPos chunk) {
    player.snapTo(centerPosition(chunk), 0, 0);
    player.level().getChunkSource().move(player);
  }

  public void close() {
    try {
      if (reentry != null) reentry.close();
      if (phase == Phase.WAIT_FOR_RESPAWN_TICKET || phase == Phase.VERIFY_RESPAWN || phase == Phase.VERIFY_REENTRY)
        PortalClosureResolver.updateLinks(world.level, originalLinks);
      peer.close();
    } finally {
      world.player.updateOptions(originalOptions);
      world.level.getChunkSource().setViewDistance(originalDistance);
    }
  }

  private enum Phase {
    WAIT_FOR_RESPAWN_TICKET,
    VERIFY_RESPAWN,
    VERIFY_REENTRY,
    SHARE_VIEW,
    SHRINK_VIEW,
    RELEASE_OUTER_TICKET,
    VERIFY_DIMENSION_CHANGE,
    DISCONNECT_PEER,
    VERIFY_DISCONNECT
  }
}
