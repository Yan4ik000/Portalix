package dev.portalix.server.portal;

import dev.portalix.PortalixMod;
import dev.portalix.compat.minecraft.ServerPlayerMotion;
import dev.portalix.mixin.world.tracking.ChunkMapAccessor;
import dev.portalix.network.ModNetworking;
import dev.portalix.network.portal.PortalCameraViewDemandPayload;
import dev.portalix.network.portal.PortalViewDemandPayload;
import dev.portalix.network.portal.PortalViewReadyPayload;
import dev.portalix.network.portal.PortalViewStatusPayload;
import dev.portalix.platform.PortalPlatform;
import dev.portalix.platform.RegistryPlatform;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalLinkId;
import dev.portalix.portal.PortalRoutes;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.PortalViewLimits;
import dev.portalix.portal.camera.PortalEyePose;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.traversal.PortalEntityMovement;
import dev.portalix.server.PortalEntityTracking;
import dev.portalix.server.portal.PortalObservationPlanner.CameraApproach;
import dev.portalix.server.portal.PortalObservationPlanner.Viewpoint;
import dev.portalix.world.chunk.ChunkDeliveryAccess;
import dev.portalix.world.chunk.ChunkViewUnion;
import dev.portalix.world.chunk.PortalChunkObservers;
import dev.portalix.world.chunk.PortalLoadingObservers;
import dev.portalix.world.chunk.PortalRetainedTerrain;
import dev.portalix.world.chunk.PortalRetainedTerrain.Claim;
import dev.portalix.world.chunk.WarmColumnAccess;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class PortalChunkTracker implements AutoCloseable {
  private static final Supplier<TicketType> SIMULATION =
      RegistryPlatform.INSTANCE.register(
          Registries.TICKET_TYPE,
          PortalixMod.id("portal_simulation"),
          () -> new TicketType(
              0,
              TicketType.FLAG_LOADING
                  | TicketType.FLAG_SIMULATION
                  | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
  private static final int PREPARATION_TICKET_RADIUS = PortalViewLimits.CORE_RADIUS
      + ChunkLevel.byStatus(FullChunkStatus.FULL) - ChunkLevel.byStatus(FullChunkStatus.BLOCK_TICKING);
  private static final Supplier<TicketType> PREPARATION =
      RegistryPlatform.INSTANCE.register(
          Registries.TICKET_TYPE,
          PortalixMod.id("portal_preparation"),
          () -> new TicketType(0, TicketType.FLAG_LOADING));
  private final GunPortalRegistry portals;
  private final Map<ServerLevel, Long2IntMap> loadingObservers = new HashMap<>();
  private final PortalRetainedTerrain retainedTerrain = new PortalRetainedTerrain(
      ChunkLevel.byStatus(FullChunkStatus.FULL) - ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING));
  private final Map<ServerLevel, LongOpenHashSet> simulationTickets = new HashMap<>();
  private final Map<ServerLevel, LongOpenHashSet> preparationTickets = new HashMap<>();
  private final Map<UUID, PlayerTracking> playerTracking = new HashMap<>();
  private final Map<UUID, TrackingMove> trackingMoves = new HashMap<>();
  private final Map<UUID, SimulationView> simulationViews = new HashMap<>();
  private final Set<UUID> observersToRefresh = new HashSet<>();

  public static void initialize() {}

  public PortalChunkTracker(GunPortalRegistry portals) {
    this.portals = portals;
  }

  public void requestViews(ServerPlayer player, PortalViewDemandPayload payload) {
    requestCameraViews(player, new PortalCameraViewDemandPayload(payload, 0, List.of()));
  }

  public void requestCameraViews(ServerPlayer player, PortalCameraViewDemandPayload payload) {
    if (PortalTopology.links(player.level()).isEmpty()) return;
    var tracking = trackingFor(player);
    tracking.deferredDemand = null;
    var views = payload.views();
    var frame = new ArrayList<PortalLink>(views.cameraFrame().size());
    for (var id : views.cameraFrame()) {
      var link = PortalTopology.resolve(player.level(), id);
      if (link == null) return;
      frame.add(link);
    }
    validateCameraFrame(player, tracking);
    var previous = tracking.camera.frame();
    var bodyEye = PortalEyePose.bodyPosition(player, 1);
    tracking.initializeEye(bodyEye);
    var eyePath = PortalEyePose.eyePath(player, PortalEyePose.interpolatedCenter(player, 1), bodyEye);
    double boom = cameraBoomReach(player, payload.detachedDistance());
    double allowance = eyeAllowance(player);
    var previousEye = tracking.previousEye != null && player.level().getGameTime() <= tracking.eyeSampleTick + 1
        ? tracking.previousEye : bodyEye;
    if (!tracking.camera.request(frame, eyePath, previousEye, bodyEye, boom,
        allowance, link -> PortalEntityMovement.canTraverse(player, link))) {
      tracking.deferredDemand = new DeferredDemand(payload, boom, allowance, player.getRootVehicle());
      return;
    }
    boolean sameRoutes = payload.waitingPath().isEmpty() ? PortalObservationPlanner.sameDemand(
        tracking.routes, views.routes(), frame, id -> PortalTopology.resolve(player.level(), id))
        : tracking.routes.equals(views.routes());
    if (!previous.equals(tracking.camera.frame()) || !sameRoutes
        || !tracking.waitingPath.equals(payload.waitingPath()) || tracking.detachedDistance != payload.detachedDistance()) {
      tracking.routes = views.routes();
      tracking.waitingPath = payload.waitingPath();
      tracking.detachedDistance = payload.detachedDistance();
      tracking.demandChanged = true;
    }
  }

  public void clientTickEnded(ServerPlayer player) {
    if (PortalTopology.links(player.level()).isEmpty()) return;
    var tracking = trackingFor(player);
    Vec3 eye = PortalEyePose.bodyPosition(player, 1);
    boolean moved = !eye.equals(tracking.eyeAnchor);
    tracking.previousEye = tracking.eyeAnchor == null ? eye : tracking.eyeAnchor;
    tracking.eyeAnchor = eye;
    tracking.eyeSampleTick = player.level().getGameTime();
    var deferred = tracking.deferredDemand;
    if (deferred != null && (moved || deferred.vehicle != player.getRootVehicle()
        || deferred.boom != cameraBoomReach(player, deferred.payload.detachedDistance())
        || deferred.eyeAllowance != eyeAllowance(player)))
      requestCameraViews(player, deferred.payload);
  }

  public void cameraCrossed(ServerPlayer player, PortalLink link) {
    var tracking = playerTracking.get(player.getUUID());
    if (tracking == null || !tracking.matches(player)) {
      tracking = trackingFor(player);
    } else if (tracking.eyeAnchor != null) {
      var transform = link.transform();
      tracking.previousEye = transform.point(tracking.previousEye);
      tracking.eyeAnchor = transform.point(tracking.eyeAnchor);
    }
    var level = player.level();
    long now = level.getGameTime();
    var visibility = PlayerPortalVisibility.of(player);
    if (canLoadChunks(player)) {
      var delivery = (ChunkDeliveryAccess) player.connection.chunkSender;
      var map = (ChunkMapAccessor) level.getChunkSource().chunkMap;
      int limit = map.portalix$playerViewDistance(player);
      var warm = tracking.warm;
      var loading = tracking.warmColumns;
      var source = PortalViewLimits.frontColumn(link.entrance().endpoint());
      var target = PortalViewLimits.frontColumn(link.exit().endpoint());
      warm.crossed(source, target, player.getChunkTrackingView(), limit, now, delivery.portalix$presentColumns(level),
          key -> {
            if (!delivery.portalix$delivered(level, key)) return false;
            if (!warm.contains(key)) {
              var holder = map.portalix$getVisibleChunk(key);
              if (holder == null || holder.getTicketLevel() > ChunkLevel.byStatus(FullChunkStatus.BLOCK_TICKING)
                  || holder.getTickingChunk() == null) return false;
            }
            return delivery.portalix$retain(player, key);
          }, loading::portalix$retainColumn);
    }
    visibility.portalCrossed(now);
    tracking.loadingObservers.portalix$loadingDelayChanged(player);
    tracking.camera.crossed(link);
    tracking.deferredDemand = null;
    tracking.waitingPath = List.of();
  }

  public void resetCamera(ServerPlayer player) {
    PlayerPortalVisibility.of(player).resumeLoading();
    EntityPortalState.of(player).resetCamera();
    var tracking = playerTracking.get(player.getUUID());
    if (tracking == null) return;
    tracking.loadingObservers.portalix$loadingDelayChanged(player);
    if (tracking.level == player.level()) tracking.warm.clear(key -> releaseColumn(player, tracking.warmColumns, key));
    else tracking.clearWarm();
    tracking.camera.clear();
    tracking.deferredDemand = null;
    tracking.routes = List.of();
    tracking.waitingPath = List.of();
    tracking.detachedDistance = 0;
    tracking.previousEye = tracking.eyeAnchor = PortalTopology.links(player.level()).isEmpty()
        ? null : PortalEyePose.bodyPosition(player, 1);
    tracking.eyeSampleTick = player.level().getGameTime();
    tracking.demandChanged = true;
  }

  private static double cameraBoomReach(ServerPlayer player, int detachedDistance) {
    return PortalPlatform.INSTANCE.maximumCameraDistance(player, detachedDistance) + .1; // Include native view bobbing.
  }

  private static double eyeAllowance(ServerPlayer player) {
    return player.getEyeHeight(Pose.STANDING)
        + EntityPortalState.of(player).eyeCorrection(player.tickCount).length();
  }

  private static void validateCameraFrame(ServerPlayer player, PlayerTracking tracking) {
    for (var link : tracking.camera.frame()) {
      if (!link.equals(PortalTopology.resolve(player.level(), PortalLinkId.fromLink(link)))) {
        tracking.camera.clear();
        tracking.routes = List.of();
        tracking.waitingPath = List.of();
        tracking.demandChanged = true;
        return;
      }
    }
  }

  private PlayerTracking trackingFor(ServerPlayer player) {
    var id = player.getUUID();
    var previous = playerTracking.get(id);
    if (previous != null && previous.matches(player)) return previous;

    retainedTerrain.release(id);
    var visibility = PlayerPortalVisibility.of(player);
    if (previous != null && previous.level == player.level() && previous.player.connection == player.connection) {
      // Same-dimension respawn replaces the player and keeps the delivered chunks.
      visibility.resetObservation(PlayerPortalVisibility.of(previous.player).chunks());
      previous.warm.clear(key -> releaseColumn(player, previous.warmColumns, key));
    } else {
      if (previous != null) previous.clearWarm();
      visibility.resetObservation(new LongOpenHashSet());
    }
    var tracking = new PlayerTracking(player);
    playerTracking.put(id, tracking);
    return tracking;
  }

  public void acknowledgeView(ServerPlayer player, PortalViewReadyPayload payload) {
    var level = player.level();
    var link = PortalTopology.find(level, payload.entrance(), payload.generation());
    if (link == null) return;
    var visibility = PlayerPortalVisibility.of(player);
    if (visibility.isReady(link)) {
      sendViewStatus(player, new PortalViewStatusPayload(payload.entrance(), payload.generation(), true));
      return;
    }
    var delivery = (ChunkDeliveryAccess) player.connection.chunkSender;
    var present = delivery.portalix$presentColumns(level);
    if (!PortalViewLimits.hasExitCore(link.exit().endpoint(), present::contains)) return;
    boolean confirmed = PortalViewLimits.hasExitCore(link.exit().endpoint(),
        key -> delivery.portalix$received(level, key));
    visibility.acknowledge(link, confirmed);
    if (confirmed) {
      sendViewStatus(player, new PortalViewStatusPayload(payload.entrance(), payload.generation(), true));
      viewReady(player);
    }
  }

  public static void sendViewStatus(ServerPlayer player, PortalViewStatusPayload status) {
    if (ModNetworking.canPlayerReceive(player, PortalViewStatusPayload.TYPE))
      ModNetworking.sendToPlayer(player, status);
  }

  public void chunkRefreshConfirmed(ServerPlayer player) {
    var delivery = (ChunkDeliveryAccess) player.connection.chunkSender;
    var level = player.level();
    if (PlayerPortalVisibility.of(player).confirmViews(key -> delivery.portalix$received(level, key),
        status -> sendViewStatus(player, status)))
      viewReady(player);
  }

  private void viewReady(ServerPlayer player) {
    var tracking = playerTracking.get(player.getUUID());
    if (tracking != null && tracking.deferredDemand != null)
      requestCameraViews(player, tracking.deferredDemand.payload);
    observersToRefresh.add(player.getUUID());
  }

  public void tick(MinecraftServer server) {
    var players = server.getPlayerList().getPlayers();
    var active = new HashSet<UUID>();
    for (var player : players) {
      active.add(player.getUUID());
      trackingFor(player);
      if (!canLoadChunks(player)) {
        retainedTerrain.release(player.getUUID());
        continue;
      }
      retainedTerrain.observe(player.getUUID(), portals.unlinkedEndpoint(player), player.getChunkTrackingView(),
          PortalViewLimits.preview(((ChunkMapAccessor) player.level().getChunkSource().chunkMap).portalix$playerViewDistance(player)),
          position -> player.level().getChunkSource().getChunkNow(ChunkPos.getX(position), ChunkPos.getZ(position)) != null);
    }
    retainedTerrain.retainPlayers(active);
    boolean simulationChanged = false;
    for (var player : players) {
      update(player, false);
      var simulation = new SimulationView(
          player.level(), PlayerPortalVisibility.of(player).simulationChunks(), canLoadChunks(player));
      var previous = simulationViews.put(player.getUUID(), simulation);
      simulationChanged |=
          previous == null
              || previous.level != simulation.level
              || previous.chunks != simulation.chunks
              || previous.loading != simulation.loading;
    }
    simulationChanged |= simulationViews.keySet().retainAll(active);
    playerTracking.entrySet().removeIf(entry -> {
      if (active.contains(entry.getKey())) return false;
      entry.getValue().clearWarm();
      return true;
    });
    var desiredLoading = new HashMap<ServerLevel, Long2IntMap>();
    var desiredPreparation = new HashMap<ServerLevel, LongOpenHashSet>();
    for (var player : players) {
      if (!canLoadChunks(player)) continue;
      var tracking = playerTracking.get(player.getUUID());
      int loadingRadius = ((ChunkMapAccessor) player.level().getChunkSource().chunkMap).portalix$serverViewDistance();
      int sourceWeight = loadingRadius - PlayerPortalVisibility.of(player).viewRadius();
      for (long center : tracking.centers)
        addLoadingSource(desiredLoading, player.level(), center, sourceWeight);
      if (tracking.retained != null)
        addLoadingSource(desiredLoading, player.level(), tracking.retained.center().pack(),
            loadingRadius - tracking.retained.radius());
      if (tracking.preparationCenter != null)
        desiredPreparation.computeIfAbsent(player.level(), ignored -> new LongOpenHashSet())
            .add(tracking.preparationCenter.longValue());
      if (!tracking.safetyCenters.isEmpty())
        desiredPreparation.computeIfAbsent(player.level(), ignored -> new LongOpenHashSet())
            .addAll(tracking.safetyCenters);
    }
    var loadingPriorities = new HashMap<ServerLevel, List<PortalChunkObservers>>();
    for (var player : players)
      if (canLoadChunks(player) && desiredLoading.containsKey(player.level()))
        loadingPriorities.computeIfAbsent(player.level(), ignored -> new ArrayList<>())
            .add(PortalChunkObservers.of(player));
    updateLoadingObservers(desiredLoading, loadingPriorities);
    updateTickets(
        preparationTickets, desiredPreparation, PREPARATION.get(), PREPARATION_TICKET_RADIUS);
    if (simulationChanged) {
      var desiredSimulation = new HashMap<ServerLevel, LongOpenHashSet>();
      for (var player : players) {
        if (!canLoadChunks(player)) continue;
        var observation = PlayerPortalVisibility.of(player);
        if (observation.eyes().isEmpty()) continue;
        var simulation =
            desiredSimulation.computeIfAbsent(player.level(), ignored -> new LongOpenHashSet());
        simulation.addAll(observation.simulationChunks());
      }
      updateTickets(simulationTickets, desiredSimulation, SIMULATION.get(), 2);
    }
    if (!observersToRefresh.isEmpty()) {
      var refresh = List.copyOf(observersToRefresh);
      observersToRefresh.clear();
      for (var id : refresh) {
        var player = server.getPlayerList().getPlayer(id);
        if (player != null) PortalEntityTracking.refreshObserver(player);
      }
    }
  }

  private static void requestChunk(ServerPlayer player, long key) {
    var level = player.level();
    var sender = player.connection.chunkSender;
    var delivery = (ChunkDeliveryAccess) sender;
    if (delivery.portalix$watching(level, key) || delivery.portalix$retain(player, key)) return;
    var map = level.getChunkSource().chunkMap;
    var holder = ((ChunkMapAccessor) map).portalix$getVisibleChunk(key);
    // The ready-to-send callback covers chunks that have yet to reach the ticking state.
    var chunk = holder == null ? null : holder.getTickingChunk();
    if (chunk != null) ChunkMapAccessor.portalix$markChunkPendingToSend(player, chunk);
  }

  private static void updateTickets(
      Map<ServerLevel, LongOpenHashSet> current,
      Map<ServerLevel, LongOpenHashSet> desired,
      TicketType type,
      int radius) {
    Set<ServerLevel> levels = new HashSet<>(current.keySet());
    levels.addAll(desired.keySet());
    for (var level : levels) {
      var previous = current.getOrDefault(level, new LongOpenHashSet());
      var next = desired.getOrDefault(level, new LongOpenHashSet());
      for (long key : previous)
        if (!next.contains(key))
          level.getChunkSource().removeTicketWithRadius(type, ChunkPos.unpack(key), radius);
      for (long key : next)
        if (!previous.contains(key))
          level.getChunkSource().addTicketWithRadius(type, ChunkPos.unpack(key), radius);
    }
    current.clear();
    current.putAll(desired);
  }

  private void updateLoadingObservers(
      Map<ServerLevel, Long2IntMap> desired,
      Map<ServerLevel, List<PortalChunkObservers>> priorities) {
    var levels = new HashSet<>(loadingObservers.keySet());
    levels.addAll(desired.keySet());
    for (var level : levels) {
      var previous = loadingObservers.get(level);
      var next = desired.getOrDefault(level, Long2IntMaps.EMPTY_MAP);
      if (next.equals(previous)) {
        next = previous;
        desired.put(level, next);
      }
      ((PortalLoadingObservers) level.getChunkSource().chunkMap.getDistanceManager())
          .portalix$observeLoading(next, priorities.getOrDefault(level, List.of()));
    }
    loadingObservers.clear();
    loadingObservers.putAll(desired);
  }

  private static void addLoadingSource(Map<ServerLevel, Long2IntMap> sources,
      ServerLevel level, long center, int distance) {
    var positions = sources.computeIfAbsent(level, ignored -> new Long2IntOpenHashMap());
    positions.put(center, Math.min(distance, positions.getOrDefault(center, Integer.MAX_VALUE)));
  }

  private static Viewpoint viewpoint(ServerPlayer player, PlayerTracking tracking, Vec3 velocity) {
    validateCameraFrame(player, tracking);
    Vec3 eye = PortalEyePose.bodyPosition(player, 1);
    tracking.initializeEye(eye);
    var camera = tracking.camera;
    double boom = cameraBoomReach(player, tracking.detachedDistance);
    Vec3 bodyEye = eye;
    if (!camera.stillReachable(player.level().getGameTime(), () -> PortalCameraFrame.reachable(
        camera.frame(), PortalEyePose.eyePath(player, PortalEyePose.interpolatedCenter(player, 1), bodyEye),
        bodyEye, bodyEye, boom, eyeAllowance(player), link -> PortalEntityMovement.canTraverse(player, link)))) {
      if (tracking.deferredDemand == null) {
        var views = new PortalViewDemandPayload(
            camera.frame().stream().map(PortalLinkId::fromLink).toList(), tracking.routes);
        var demand = new PortalCameraViewDemandPayload(views, tracking.detachedDistance, tracking.waitingPath);
        tracking.deferredDemand = new DeferredDemand(demand, boom, eyeAllowance(player), player.getRootVehicle());
      }
      camera.clear();
      tracking.routes = List.of();
      tracking.waitingPath = List.of();
      tracking.demandChanged = true;
    }
    var approach = cameraApproach(player, tracking, bodyEye, boom);
    var frame = camera.frame();
    for (var link : frame) {
      eye = link.transform().point(eye);
      velocity = link.transform().vector(velocity);
    }
    return new Viewpoint(eye, velocity, frame, boom, approach);
  }

  private static @Nullable CameraApproach cameraApproach(
      ServerPlayer player, PlayerTracking tracking, Vec3 eye, double boom) {
    if (tracking.waitingPath.isEmpty() || !PortalEntityMovement.vehicleAllowed(player)) {
      tracking.waitingCheck = null;
      return null;
    }
    var level = player.level();
    long now = level.getGameTime();
    var topology = PortalTopology.links(level);
    double allowance = eyeAllowance(player);
    var previousEye = tracking.previousEye != null && now <= tracking.eyeSampleTick + 1
        ? tracking.previousEye : eye;
    var bodyPath = PortalEyePose.eyePath(player, PortalEyePose.interpolatedCenter(player, 1), eye);
    var checked = tracking.waitingCheck;
    boolean samePath = checked != null && checked.topology == topology
        && checked.ids.equals(tracking.waitingPath) && checked.boom == boom && checked.allowance == allowance;
    // The latest request may change several times in one server tick.
    if ((!samePath || !checked.eye.equals(eye) || !checked.previousEye.equals(previousEye)
        || !checked.bodyPath.equals(bodyPath)) && now >= tracking.nextWaitingCheck) {
      tracking.nextWaitingCheck = now + PortalCameraFrame.RECHECK_TICKS;
      var path = witnessWaitingPath(level, tracking.waitingPath, bodyPath, previousEye, eye, boom, allowance);
      checked = new WaitingCameraCheck(topology, tracking.waitingPath, eye, previousEye, bodyPath, boom, allowance, path);
      tracking.waitingCheck = checked;
      samePath = true;
    }
    if (!samePath || checked.path.isEmpty()) return null;
    var path = checked.path;
    var prefix = path.subList(0, path.size() - 1);
    for (var link : prefix) {
      if (!PortalEntityMovement.canTraverse(player, link)) return null;
      eye = link.transform().point(eye);
    }
    return new CameraApproach(eye, path.getLast());
  }

  private static List<PortalLink> witnessWaitingPath(
      ServerLevel level, List<PortalLinkId> ids, List<PortalLink> bodyPath,
      Vec3 previousEye, Vec3 eye, double boom, double allowance) {
    var path = new ArrayList<PortalLink>(ids.size());
    PortalLink previous = null;
    for (int index = 0; index < ids.size(); index++) {
      var link = PortalTopology.resolve(level, ids.get(index));
      // Camera-frame prefixes are simplified. The last link describes an actual crossing attempt.
      if (link == null || (index + 1 < ids.size() && previous != null
          && PortalRoutes.sameIdentity(previous.inverse(), link))) return List.of();
      path.add(link);
      previous = link;
    }
    return PortalViewLimits.canCross(path.subList(0, path.size() - 1), path.getLast())
        && PortalCameraFrame.reachable(path, bodyPath, previousEye, eye, boom, allowance, link -> true)
        ? List.copyOf(path) : List.of();
  }

  private void update(ServerPlayer player, boolean captureUnlinkedTerrain) {
    var level = player.level();
    var chunkSource = level.getChunkSource();
    var chunkMap = (ChunkMapAccessor) chunkSource.chunkMap;
    int primaryRadius = chunkMap.portalix$playerViewDistance(player);
    int radius = PortalViewLimits.preview(primaryRadius);
    var visibility = PlayerPortalVisibility.of(player);
    PlayerTracking tracking = trackingFor(player);
    boolean loading = canLoadChunks(player);
    var endpoint = loading ? portals.unlinkedEndpoint(player) : null;
    if (captureUnlinkedTerrain)
      retainedTerrain.observe(player.getUUID(), endpoint, player.getChunkTrackingView(), radius,
          position -> chunkSource.getChunkNow(ChunkPos.getX(position), ChunkPos.getZ(position)) != null);
    var retained = loading ? retainedTerrain.claim(player.getUUID(), chunkMap.portalix$serverViewDistance()) : null;
    Long preparationCenter = endpoint == null ? null : PortalViewLimits.frontColumn(endpoint).pack();
    var links = PortalTopology.links(level);
    var eye = links.isEmpty() ? Vec3.ZERO : player.getEyePosition();
    var velocity = links.isEmpty() ? Vec3.ZERO : ServerPlayerMotion.observationMovement(player);
    var view = links.isEmpty() ? null : viewpoint(player, tracking, velocity);
    boolean topologyChanged = links != tracking.links;
    if (topologyChanged) {
      visibility.retainLinks(links);
      if (links.isEmpty()) tracking.previousEye = tracking.eyeAnchor = null;
    }
    long now = level.getGameTime();
    int trackingRadius = visibility.trackingDistance(now, primaryRadius);
    if (loading)
      tracking.warm.advance(now, primaryRadius, tracking::canRelease,
          key -> releaseColumn(player, tracking.warmColumns, key));
    else tracking.warm.clear(key -> releaseColumn(player, tracking.warmColumns, key));
    boolean replan = tracking.primaryRadius != trackingRadius || topologyChanged || tracking.demandChanged
        || !Objects.equals(view, tracking.view) || visibility.viewRadius() != radius
        || !eye.equals(tracking.eye) || !velocity.equals(tracking.velocity);
    boolean chunksChanged = visibility.viewRadius() != radius || !Objects.equals(tracking.preparationCenter, preparationCenter)
        || !Objects.equals(tracking.retained, retained);
    var observers = visibility.observers();
    var simulation = visibility.simulationChunks();
    tracking.primaryRadius = trackingRadius;
    if (replan) {
      var plan = PortalObservationPlanner.plan(eye, velocity, trackingRadius,
          (point, distance) -> PortalViewLimits.nearbyEntrances(level, point, distance * 16.0),
          view, tracking.routes,
          id -> PortalTopology.resolve(level, id));
      tracking.view = view;
      tracking.demandChanged = false;
      chunksChanged |= !tracking.centers.equals(plan.centers());
      chunksChanged |= !tracking.safetyCenters.equals(plan.safetyCenters());
      if (!tracking.simulationCenters.equals(plan.simulationCenters())) {
        simulation = new LongOpenHashSet();
        for (long center : plan.simulationCenters()) addCore(simulation, center);
        chunksChanged = true;
      }
      tracking.links = links;
      tracking.eye = eye;
      tracking.velocity = velocity;
      tracking.centers = plan.centers();
      tracking.safetyCenters = plan.safetyCenters();
      tracking.simulationCenters = plan.simulationCenters();
      observers = plan.observers();
    }
    if (!chunksChanged && !replan) return;
    tracking.preparationCenter = preparationCenter;
    tracking.retained = retained;
    var previous = visibility.chunks();
    var chunks = previous;
    if (chunksChanged) {
      chunks = ChunkViewUnion.collect(tracking.centers, radius);
      for (long center : tracking.safetyCenters) addCore(chunks, center);
      if (retained != null) retained.addChunks(chunks);
      if (preparationCenter != null) addCore(chunks, preparationCenter);
      chunks.addAll(simulation);
    }
    var previousEyes = visibility.eyes();
    visibility.observe(chunks, observers, simulation, radius);
    if (!visibility.eyes().equals(previousEyes)) observersToRefresh.add(player.getUUID());
    if (chunksChanged && !trackingMoves.containsKey(player.getUUID())) reconcile(player, previous, chunks);
  }

  private static void reconcile(ServerPlayer player, LongOpenHashSet previous, LongOpenHashSet current) {
    if (previous == current) return;
    var primary = player.getChunkTrackingView();
    for (long key : previous)
      if (!current.contains(key) && !primary.contains(ChunkPos.getX(key), ChunkPos.getZ(key)))
        ChunkMapAccessor.portalix$dropChunk(player, ChunkPos.unpack(key));
    for (long key : current) if (!previous.contains(key)) requestChunk(player, key);
  }

  private static void addCore(LongOpenHashSet chunks, long center) {
    int cx = ChunkPos.getX(center), cz = ChunkPos.getZ(center);
    for (int x = cx - PortalViewLimits.CORE_RADIUS; x <= cx + PortalViewLimits.CORE_RADIUS; x++)
      for (int z = cz - PortalViewLimits.CORE_RADIUS; z <= cz + PortalViewLimits.CORE_RADIUS; z++)
        chunks.add(ChunkPos.pack(x, z));
  }

  public void moveStarted(ServerPlayer player) {
    var map = (ChunkMapAccessor) player.level().getChunkSource().chunkMap;
    moveStarted(player, map.portalix$playerViewDistance(player));
    // ChunkMap.move applies the published eyes to its entity trackers before moving the view.
    observersToRefresh.remove(player.getUUID());
  }

  public void moveStarted(ServerPlayer player, int radius) {
    var move = trackingMoves.get(player.getUUID());
    if (move != null) {
      move.depth++;
      update(player, true);
      return;
    }
    if (player.getChunkTrackingView() instanceof ChunkTrackingView.Positioned current
        && current.center().equals(player.chunkPosition())
        && current.viewDistance() == PlayerPortalVisibility.of(player).trackingDistance(
            player.level().getGameTime(), radius)) {
      update(player, true);
      return;
    }
    trackingFor(player);
    var previous = PlayerPortalVisibility.of(player).chunks();
    trackingMoves.put(player.getUUID(), new TrackingMove(previous));
    update(player, true);
  }

  public boolean moveFinished(ServerPlayer player) {
    var move = trackingMoves.get(player.getUUID());
    if (move == null) return true;
    if (--move.depth != 0) return false;
    trackingMoves.remove(player.getUUID());
    reconcile(player, move.previous, PlayerPortalVisibility.of(player).chunks());
    return true;
  }

  public boolean retainedDuringMove(ServerPlayer player, long chunk) {
    var move = trackingMoves.get(player.getUUID());
    return move != null
        && move.previous.contains(chunk)
        && ((ChunkDeliveryAccess) player.connection.chunkSender)
            .portalix$retain(player, chunk);
  }

  public void disconnect(ServerPlayer player) {
    observersToRefresh.remove(player.getUUID());
    retainedTerrain.release(player.getUUID());
    var tracking = playerTracking.remove(player.getUUID());
    if (tracking != null) tracking.clearWarm();
    trackingMoves.remove(player.getUUID());
    var visibility = PlayerPortalVisibility.of(player);
    visibility.resetObservation(new LongOpenHashSet());
    visibility.clearReady();
    if (tracking != null) tracking.loadingObservers.portalix$loadingDelayChanged(player);
  }

  @Override
  public void close() {
    observersToRefresh.clear();
    updateLoadingObservers(new HashMap<>(), Map.of());
    updateTickets(simulationTickets, Map.of(), SIMULATION.get(), 2);
    updateTickets(preparationTickets, Map.of(), PREPARATION.get(), PREPARATION_TICKET_RADIUS);
    playerTracking.values().forEach(PlayerTracking::clearWarm);
    playerTracking.clear();
    retainedTerrain.clear();
    trackingMoves.clear();
    simulationViews.clear();
  }

  public boolean retainsTerrain(ServerPlayer player, long column) {
    var tracking = playerTracking.get(player.getUUID());
    return tracking != null && tracking.level == player.level() && tracking.warm.contains(column);
  }

  private static void releaseColumn(ServerPlayer player, WarmColumnAccess loading, long column) {
    loading.portalix$releaseColumn(column);
    if (!PlayerPortalVisibility.of(player).contains(column)
        && !player.getChunkTrackingView().contains(ChunkPos.getX(column), ChunkPos.getZ(column)))
      ChunkMapAccessor.portalix$dropChunk(player, ChunkPos.unpack(column));
  }

  private static final class PlayerTracking {
    final ServerLevel level;
    final ServerPlayer player;
    final PortalCameraFrame camera = new PortalCameraFrame();
    List<List<PortalLinkId>> routes = List.of();
    List<PortalLinkId> waitingPath = List.of();
    int detachedDistance;
    @Nullable WaitingCameraCheck waitingCheck;
    long nextWaitingCheck;
    @Nullable Vec3 previousEye;
    @Nullable Vec3 eyeAnchor;
    long eyeSampleTick;
    Viewpoint view;
    boolean demandChanged;
    @Nullable DeferredDemand deferredDemand;
    LongOpenHashSet centers = new LongOpenHashSet();
    LongOpenHashSet safetyCenters = new LongOpenHashSet();
    LongOpenHashSet simulationCenters = new LongOpenHashSet();
    @Nullable Long preparationCenter;
    @Nullable Claim retained;
    final PortalWarmTerrain warm = new PortalWarmTerrain();
    final PortalLoadingObservers loadingObservers;
    final WarmColumnAccess warmColumns;
    final TicketStorage tickets;
    int primaryRadius;
    List<PortalLink> links = List.of();
    Vec3 eye = Vec3.ZERO;
    Vec3 velocity = Vec3.ZERO;

    PlayerTracking(ServerPlayer player) {
      this.player = player;
      this.level = player.level();
      loadingObservers = (PortalLoadingObservers) level.getChunkSource().chunkMap.getDistanceManager();
      warmColumns = loadingObservers.portalix$warmColumns();
      tickets = level.getChunkSource().getDataStorage().computeIfAbsent(TicketStorage.TYPE);
    }

    boolean matches(ServerPlayer observer) {
      return player == observer && level == observer.level();
    }

    void initializeEye(Vec3 eye) {
      if (eyeAnchor != null) return;
      previousEye = eyeAnchor = eye;
      eyeSampleTick = level.getGameTime();
    }

    boolean canRelease(long column) {
      int x = ChunkPos.getX(column), z = ChunkPos.getZ(column);
      if (!player.getChunkTrackingView().contains(x, z) && !PlayerPortalVisibility.of(player).contains(column)) return true;
      // The tracking view expands before native loading tickets have actually reached every column.
      int required = ChunkLevel.byStatus(FullChunkStatus.BLOCK_TICKING);
      if (tickets.getTicketLevelAt(column, false) <= required) return true;
      for (int dx = -1; dx <= 1; dx++)
        for (int dz = -1; dz <= 1; dz++)
          if ((dx != 0 || dz != 0) && tickets.getTicketLevelAt(ChunkPos.pack(x + dx, z + dz), false) < required)
            return true;
      return false;
    }

    void clearWarm() {
      warm.clear(warmColumns::portalix$releaseColumn);
    }
  }

  private static final class TrackingMove {
    final LongOpenHashSet previous;
    int depth = 1;

    TrackingMove(LongOpenHashSet previous) {
      this.previous = previous;
    }
  }

  private record DeferredDemand(PortalCameraViewDemandPayload payload, double boom, double eyeAllowance, Entity vehicle) {}

  private record WaitingCameraCheck(List<PortalLink> topology, List<PortalLinkId> ids, Vec3 eye,
      Vec3 previousEye, List<PortalLink> bodyPath, double boom, double allowance, List<PortalLink> path) {}

  private record SimulationView(ServerLevel level, LongOpenHashSet chunks, boolean loading) {}

  private static boolean canLoadChunks(ServerPlayer player) {
    return !player.isSpectator() || player.level().getGameRules().get(GameRules.SPECTATORS_GENERATE_CHUNKS);
  }
}
