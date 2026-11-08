package dev.portalix.server.portal;

import dev.portalix.PortalixMod;
import dev.portalix.api.PortalEndpoint;
import dev.portalix.mixin.world.tracking.ChunkMapAccessor;
import dev.portalix.network.portal.PortalViewReadyPayload;
import dev.portalix.platform.RegistryPlatform;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.world.chunk.ChunkDeliveryAccess;
import dev.portalix.world.chunk.ChunkViewUnion;
import dev.portalix.world.chunk.PortalLoadingObservers;
import dev.portalix.world.chunk.PortalChunkObservers;
import dev.portalix.world.chunk.PortalRetainedTerrain;
import dev.portalix.world.chunk.PortalRetainedTerrain.Claim;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
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
  private static final int PORTAL_SIMULATION_RADIUS = 1;
  private static final int PREPARATION_TICKET_RADIUS = PORTAL_SIMULATION_RADIUS
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
  private final Map<UUID, Pending> pending = new HashMap<>();
  private final Map<UUID, Handoff> handoffs = new HashMap<>();
  private final Map<UUID, Observed> observedChunks = new HashMap<>();

  public static void initialize() {}

  public PortalChunkTracker(GunPortalRegistry portals) {
    this.portals = portals;
  }

  public boolean acknowledgeView(ServerPlayer player, PortalViewReadyPayload payload) {
    var level = player.level();
    var link = PortalTopology.find(level, payload.entrance(), payload.generation());
    if (link == null) return false;
    var visibility = PlayerPortalVisibility.of(player);
    if (visibility.isReady(link)) return false;
    long exit = chunkPosition(link.exit().endpoint().center());
    int x = ChunkPos.getX(exit), z = ChunkPos.getZ(exit);
    var present = ((ChunkDeliveryAccess) player.connection.chunkSender).portalix$presentColumns(level);
    for (int dx = -1; dx <= 1; dx++)
      for (int dz = -1; dz <= 1; dz++)
        if (!present.contains(ChunkPos.pack(x + dx, z + dz))) return false;

    visibility.ready(link);
    return true;
  }

  public void tick(MinecraftServer server) {
    int viewDistance = server.getPlayerList().getViewDistance();
    int loadingRadius = Math.clamp(viewDistance, 2, 32);
    var active = new HashSet<UUID>();
    boolean changed = false;
    for (var player : server.getPlayerList().getPlayers()) {
      active.add(player.getUUID());
      var waiting = pending.get(player.getUUID());
      if (waiting != null && waiting.level != player.level()) retainedTerrain.release(player.getUUID());
      retainedTerrain.observe(player.getUUID(), portals.unlinkedEndpoint(player), player.getChunkTrackingView(),
          viewRadius(player, loadingRadius),
          position -> player.level().getChunkSource().getChunkNow(ChunkPos.getX(position), ChunkPos.getZ(position)) != null);
    }
    retainedTerrain.retainPlayers(active);
    for (var player : server.getPlayerList().getPlayers()) {
      update(player, loadingRadius, false);
      var observed = new Observed(player.level(), PlayerPortalVisibility.of(player).simulationChunks());
      var previous = observedChunks.put(player.getUUID(), observed);
      changed |=
          previous == null
              || previous.level != observed.level
              || previous.simulation != observed.simulation;
    }
    changed |= observedChunks.keySet().retainAll(active);
    pending.keySet().retainAll(active);
    var desiredLoading = new HashMap<ServerLevel, Long2IntMap>();
    var desiredPreparation = new HashMap<ServerLevel, LongOpenHashSet>();
    for (var player : server.getPlayerList().getPlayers()) {
      var waiting = pending.get(player.getUUID());
      int sourceWeight = loadingRadius - PlayerPortalVisibility.of(player).viewRadius();
      for (long center : waiting.centers)
        addLoadingSource(desiredLoading, player.level(), center, sourceWeight);
      if (waiting.retained != null)
        addLoadingSource(desiredLoading, player.level(), waiting.retained.center().pack(),
            loadingRadius - waiting.retained.radius());
      if (waiting.preparation != null)
        desiredPreparation.computeIfAbsent(player.level(), ignored -> new LongOpenHashSet())
            .add(waiting.preparation.longValue());
    }
    var loadingPriorities = new HashMap<ServerLevel, List<PortalChunkObservers>>();
    for (var player : server.getPlayerList().getPlayers())
      if (desiredLoading.containsKey(player.level()))
        loadingPriorities.computeIfAbsent(player.level(), ignored -> new ArrayList<>())
            .add(PortalChunkObservers.of(player));
    updateLoadingObservers(desiredLoading, loadingPriorities);
    updateTickets(
        preparationTickets, desiredPreparation, PREPARATION.get(), PREPARATION_TICKET_RADIUS);
    if (changed) {
      var desiredSimulation = new HashMap<ServerLevel, LongOpenHashSet>();
      for (var player : server.getPlayerList().getPlayers()) {
        var observation = PlayerPortalVisibility.of(player);
        if (observation.eyes().isEmpty()) continue;
        var simulation =
            desiredSimulation.computeIfAbsent(player.level(), ignored -> new LongOpenHashSet());
        simulation.addAll(observation.simulationChunks());
      }
      updateTickets(simulationTickets, desiredSimulation, SIMULATION.get(), 2);
    }
  }

  private static void requestChunk(ServerPlayer player, long key) {
    var level = player.level();
    var sender = player.connection.chunkSender;
    var delivery = (ChunkDeliveryAccess) sender;
    if (delivery.portalix$watching(level, key) || delivery.portalix$retain(level, key)) return;
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

  private void update(ServerPlayer player, int viewDistance, boolean capture) {
    int radius = viewRadius(player, viewDistance);
    var state = PlayerPortalVisibility.of(player);
    Pending waiting = pending.get(player.getUUID());
    if (waiting == null || waiting.level != player.level()) {
      if (capture) retainedTerrain.release(player.getUUID());
      waiting = new Pending(player.level());
      pending.put(player.getUUID(), waiting);
      state.replace(new LongOpenHashSet(), List.of());
    }
    var endpoint = portals.unlinkedEndpoint(player);
    if (capture)
      retainedTerrain.observe(player.getUUID(), endpoint, player.getChunkTrackingView(), radius,
          position -> player.level().getChunkSource().getChunkNow(ChunkPos.getX(position), ChunkPos.getZ(position)) != null);
    var retained = retainedTerrain.claim(player.getUUID(),
        Math.clamp(player.level().getServer().getPlayerList().getViewDistance(), 2, 32));
    Long preparation = endpoint == null ? null : chunkPosition(endpoint.center());
    var links = PortalTopology.links(player.level());
    var eye = links.isEmpty() ? Vec3.ZERO : player.getEyePosition();
    var velocity = links.isEmpty() ? Vec3.ZERO : player.getDeltaMovement();
    boolean topologyChanged = links != waiting.links;
    if (topologyChanged) state.retainLinks(links);
    boolean geometryChanged = topologyChanged || state.viewRadius() != radius
        || !eye.equals(waiting.eye) || !velocity.equals(waiting.velocity);
    boolean viewsChanged = state.viewRadius() != radius || !Objects.equals(waiting.preparation, preparation)
        || !Objects.equals(waiting.retained, retained);
    var observers = state.observers();
    var simulation = state.simulationChunks();
    if (geometryChanged) {
      var geometry = observe(player.level(), eye, velocity, radius);
      viewsChanged |= !waiting.centers.equals(geometry.centers);
      if (!waiting.simulationCenters.equals(geometry.simulationCenters)) {
        simulation = new LongOpenHashSet();
        for (long center : geometry.simulationCenters) addCore(simulation, center);
        viewsChanged = true;
      }
      waiting.links = links;
      waiting.eye = eye;
      waiting.velocity = velocity;
      waiting.centers = geometry.centers;
      waiting.simulationCenters = geometry.simulationCenters;
      observers = geometry.observers;
    }
    if (!viewsChanged && !geometryChanged) return;
    waiting.preparation = preparation;
    waiting.retained = retained;
    var previous = state.chunks();
    var chunks = previous;
    if (viewsChanged) {
      chunks = ChunkViewUnion.collect(waiting.centers, radius);
      if (retained != null) retained.addChunks(chunks);
      if (preparation != null) addCore(chunks, preparation);
      chunks.addAll(simulation);
    }
    state.observe(chunks, observers, simulation, radius);
    if (viewsChanged && !handoffs.containsKey(player.getUUID())) reconcile(player, previous, chunks);
  }

  private static void reconcile(ServerPlayer player, LongOpenHashSet previous, LongOpenHashSet current) {
    if (previous == current) return;
    var primary = player.getChunkTrackingView();
    for (long key : previous)
      if (!current.contains(key) && !primary.contains(ChunkPos.getX(key), ChunkPos.getZ(key)))
        ChunkMapAccessor.portalix$dropChunk(player, ChunkPos.unpack(key));
    for (long key : current) if (!previous.contains(key)) requestChunk(player, key);
  }

  private static Geometry observe(ServerLevel level, Vec3 eye, Vec3 velocity, int radius) {
    if (PortalTopology.links(level).isEmpty())
      return new Geometry(new LongOpenHashSet(), new LongOpenHashSet(), List.of());
    double reach = (radius + 1) * 16.0;
    var centers = new LongOpenHashSet();
    var simulationCenters = new LongOpenHashSet();
    var arrivals = new HashSet<PortalEndpoint>();
    var observations = new LinkedHashMap<ObserverView, Boolean>();
    var frontier = new LinkedHashMap<Vec3, Observer>();
    for (int depth = 0; depth < 2; depth++) {
      var origins = depth == 0 ? List.of(new Observer(eye, velocity, null)) : frontier.values();
      for (var observer : origins) {
        var point = observer.eye;
        for (var link : PortalTopology.entrances(
            level, point.x - reach, point.z - reach, point.x + reach, point.z + reach)) {
          var entrance = link.entrance().endpoint();
          if (entrance.center().distanceToSqr(point) > reach * reach) continue;
          var last = observer.last;
          if (last != null && last.generation() == link.generation() && last.gun().equals(link.gun())
              && last.exit().equals(link.entrance()) && last.entrance().equals(link.exit())) continue;
          var exit = link.exit().endpoint();
          if (arrivals.add(entrance)) arrivalCentres(centers, entrance);
          if (arrivals.add(exit)) arrivalCentres(centers, exit);
          simulationCenters.add(chunkPosition(exit.center()));
          var transform = link.transform();
          var virtualEye = transform.point(point);
          centers.add(chunkPosition(virtualEye));
          var view = new ObserverView(virtualEye, exit);
          if (observations.get(view) != Boolean.TRUE)
            observations.put(view, arriving(entrance, point, observer.velocity));
          if (depth == 0)
            frontier.computeIfAbsent(virtualEye,
                key -> new Observer(key, transform.vector(observer.velocity), link));
        }
      }
    }
    var observers = new ArrayList<PlayerPortalVisibility.Observer>(observations.size());
    observations.forEach((view, arrival) ->
        observers.add(new PlayerPortalVisibility.Observer(view.eye, view.exit, arrival)));
    return new Geometry(centers, simulationCenters, observers);
  }

  private static long chunkPosition(Vec3 point) {
    int x = (int) Math.floor(point.x) >> 4, z = (int) Math.floor(point.z) >> 4;
    return ChunkPos.pack(x, z);
  }

  private static void arrivalCentres(LongOpenHashSet centers, PortalEndpoint portal) {
    var front = portal.center().add(portal.normal().scale(PortalAperture.PLANE_EPSILON));
    var right = portal.right().scale(portal.width() / 2);
    var up = portal.up().scale(portal.height() / 2);
    double widthX = Math.abs(right.x) + Math.abs(up.x);
    double widthZ = Math.abs(right.z) + Math.abs(up.z);
    int minX = SectionPos.posToSectionCoord(front.x - widthX);
    int maxX = SectionPos.posToSectionCoord(front.x + widthX);
    int minZ = SectionPos.posToSectionCoord(front.z - widthZ);
    int maxZ = SectionPos.posToSectionCoord(front.z + widthZ);
    // A 1x2 aperture spans less than one chunk on either axis, including rolled portals.
    for (int x = minX; x <= maxX; x++)
      for (int z = minZ; z <= maxZ; z++) centers.add(ChunkPos.pack(x, z));
  }

  private static void addCore(LongOpenHashSet chunks, long center) {
    int cx = ChunkPos.getX(center), cz = ChunkPos.getZ(center);
    for (int x = cx - PORTAL_SIMULATION_RADIUS; x <= cx + PORTAL_SIMULATION_RADIUS; x++)
      for (int z = cz - PORTAL_SIMULATION_RADIUS; z <= cz + PORTAL_SIMULATION_RADIUS; z++)
        chunks.add(ChunkPos.pack(x, z));
  }

  private static boolean arriving(PortalEndpoint entrance, Vec3 eye, Vec3 velocity) {
    var relative = eye.subtract(entrance.center());
    double normal = PortalAperture.signedDistance(entrance, eye);
    double right = Math.max(0, Math.abs(relative.dot(entrance.right())) - entrance.width() / 2);
    double up = Math.max(0, Math.abs(relative.dot(entrance.up())) - entrance.height() / 2);
    double approachSpeed = Math.max(0, -velocity.dot(entrance.normal())) * 20;
    double reach = Math.max(entrance.width(), entrance.height()) * 2 + approachSpeed;
    return normal * normal + right * right + up * up <= reach * reach;
  }

  public void moveStarted(ServerPlayer player, int radius) {
    var handoff = handoffs.get(player.getUUID());
    if (handoff != null) {
      handoff.depth++;
      update(player, radius, true);
      return;
    }
    if (player.getChunkTrackingView() instanceof ChunkTrackingView.Positioned current
        && current.center().equals(player.chunkPosition())
        && current.viewDistance() == viewRadius(player, radius)) {
      update(player, radius, true);
      return;
    }
    var waiting = pending.get(player.getUUID());
    var previous =
        waiting != null && waiting.level == player.level()
            ? PlayerPortalVisibility.of(player).chunks()
            : new LongOpenHashSet();
    handoffs.put(player.getUUID(), new Handoff(previous));
    update(player, radius, true);
  }

  public boolean moveFinished(ServerPlayer player) {
    var handoff = handoffs.get(player.getUUID());
    if (handoff == null) return true;
    if (--handoff.depth != 0) return false;
    handoffs.remove(player.getUUID());
    reconcile(player, handoff.previous, PlayerPortalVisibility.of(player).chunks());
    return true;
  }

  public boolean retainedDuringMove(ServerPlayer player, long chunk) {
    var handoff = handoffs.get(player.getUUID());
    return handoff != null
        && handoff.previous.contains(chunk)
        && ((ChunkDeliveryAccess) player.connection.chunkSender)
            .portalix$delivered(player.level(), chunk);
  }

  public void disconnect(ServerPlayer player) {
    retainedTerrain.release(player.getUUID());
    pending.remove(player.getUUID());
    handoffs.remove(player.getUUID());
    var visibility = PlayerPortalVisibility.of(player);
    visibility.replace(new LongOpenHashSet(), List.of());
    visibility.clearReady();
  }

  @Override
  public void close() {
    updateLoadingObservers(new HashMap<>(), Map.of());
    updateTickets(simulationTickets, Map.of(), SIMULATION.get(), 2);
    updateTickets(preparationTickets, Map.of(), PREPARATION.get(), PREPARATION_TICKET_RADIUS);
    pending.clear();
    retainedTerrain.clear();
    handoffs.clear();
    observedChunks.clear();
  }

  private static final class Pending {
    final ServerLevel level;
    LongOpenHashSet centers = new LongOpenHashSet();
    LongOpenHashSet simulationCenters = new LongOpenHashSet();
    @Nullable Long preparation;
    @Nullable Claim retained;
    List<PortalLink> links = List.of();
    Vec3 eye = Vec3.ZERO;
    Vec3 velocity = Vec3.ZERO;

    Pending(ServerLevel level) {
      this.level = level;
    }
  }

  private static final class Handoff {
    final LongOpenHashSet previous;
    int depth = 1;

    Handoff(LongOpenHashSet previous) {
      this.previous = previous;
    }
  }

  private record Observed(ServerLevel level, LongOpenHashSet simulation) {}

  private static int viewRadius(ServerPlayer player, int serverRadius) {
    int requested = player.requestedViewDistance();
    return Math.clamp(requested > 0 ? Math.min(serverRadius, requested) : serverRadius, 2, 32);
  }

  private record Observer(Vec3 eye, Vec3 velocity, PortalLink last) {}

  private record ObserverView(Vec3 eye, PortalEndpoint exit) {}

  private record Geometry(LongOpenHashSet centers, LongOpenHashSet simulationCenters,
      List<PlayerPortalVisibility.Observer> observers) {}
}
