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
    update(player, player.level().getServer().getPlayerList().getViewDistance());
    var visibility = PlayerPortalVisibility.of(player);
    for (var link : PortalTopology.links(player.level())) {
      if (link.entrance().identity() != payload.entrance()
          || link.generation() != payload.generation()) continue;
      var exit = link.exit().endpoint().center();
      int x = (int) Math.floor(exit.x) >> 4, z = (int) Math.floor(exit.z) >> 4;
      var chunks = player.level().getChunkSource();
      if (!player.getChunkTrackingView().contains(x, z) && !visibility.contains(x, z)) return false;

      visibility.ready(link);
      chunks.move(player);
      return true;
    }
    return false;
  }

  public void tick(MinecraftServer server) {
    var active = new HashSet<UUID>();
    boolean changed = false;
    for (var player : server.getPlayerList().getPlayers()) {
      active.add(player.getUUID());
      var waiting = pending.get(player.getUUID());
      if (waiting != null && waiting.level != player.level()) retainedTerrain.release(player.getUUID());
      retainedTerrain.observe(player.getUUID(), portals.unlinkedEndpoint(player), player.getChunkTrackingView(),
          viewRadius(player, server.getPlayerList().getViewDistance()),
          position -> player.level().getChunkSource().getChunkNow(ChunkPos.getX(position), ChunkPos.getZ(position)) != null);
    }
    retainedTerrain.retainPlayers(active);
    for (var player : server.getPlayerList().getPlayers()) {
      update(player, server.getPlayerList().getViewDistance());
      var chunks = PlayerPortalVisibility.of(player).chunks();
      var observed =
          new Observed(
              player.level(), chunks, PlayerPortalVisibility.of(player).simulationChunks());
      var previous = observedChunks.put(player.getUUID(), observed);
      changed |=
          previous == null
              || previous.level != observed.level
              || previous.chunks != chunks
              || previous.simulation != observed.simulation;
    }
    changed |= observedChunks.keySet().retainAll(active);
    pending.keySet().retainAll(active);
    var desiredLoading = new HashMap<ServerLevel, Long2IntMap>();
    var desiredPreparation = new HashMap<ServerLevel, LongOpenHashSet>();
    for (var player : server.getPlayerList().getPlayers()) {
      var waiting = pending.get(player.getUUID());
      for (long center : waiting.centers) addLoadingSource(desiredLoading, player.level(), center, 0);
      if (waiting.retained != null)
        addLoadingSource(desiredLoading, player.level(), waiting.retained.center().pack(),
            server.getPlayerList().getViewDistance() - waiting.retained.radius());
      if (!waiting.preparationCenters.isEmpty())
        desiredPreparation.computeIfAbsent(player.level(), ignored -> new LongOpenHashSet())
            .addAll(waiting.preparationCenters);
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
    for (var player : server.getPlayerList().getPlayers()) queuePendingChunks(player);
  }

  private void queuePendingChunks(ServerPlayer player) {
    var waiting = pending.get(player.getUUID()).chunks;
    var sender = player.connection.chunkSender;
    var delivery = (ChunkDeliveryAccess) sender;
    var iterator = waiting.iterator();
    while (iterator.hasNext()) {
      long key = iterator.nextLong();
      if (sender.isPending(key) || delivery.portalix$retain(player.level(), key)) {
        iterator.remove();
        continue;
      }
      var chunk = player.level().getChunkSource().chunkMap.getChunkToSend(key);
      if (chunk != null) {
        ChunkMapAccessor.portalix$markChunkPendingToSend(player, chunk);
        iterator.remove();
      }
    }
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

  void update(ServerPlayer player, int viewDistance) {
    int radius = viewRadius(player, viewDistance);
    var state = PlayerPortalVisibility.of(player);
    var previous = state.chunks();
    Pending waiting = pending.get(player.getUUID());
    if (waiting == null || waiting.level != player.level()) {
      retainedTerrain.release(player.getUUID());
      waiting = new Pending(player.level());
      pending.put(player.getUUID(), waiting);
      previous = new LongOpenHashSet();
      state.observe(previous, List.of(), new LongOpenHashSet(), 0);
    }
    var centers = new LongOpenHashSet();
    var simulationCenters = new LongOpenHashSet();
    var preparationCenters = new LongOpenHashSet();
    var endpoint = portals.unlinkedEndpoint(player);
    if (endpoint != null) preparationCenters.add(chunkPosition(endpoint));
    retainedTerrain.observe(player.getUUID(), endpoint, player.getChunkTrackingView(), radius,
        position -> player.level().getChunkSource().getChunkNow(ChunkPos.getX(position), ChunkPos.getZ(position)) != null);
    var retained = retainedTerrain.claim(player.getUUID(),
        player.level().getServer().getPlayerList().getViewDistance());
    var observers = new ArrayList<PlayerPortalVisibility.Observer>();
    List<Observer> frontier = List.of(
        new Observer(player.getEyePosition(), player.getDeltaMovement(), null));
    Set<Vec3> positions = new HashSet<>();
    for (int depth = 0; depth < 2; depth++) {
      var next = new ArrayList<Observer>();
      for (var observer : frontier) {
        var eye = observer.eye;
        for (var link : PortalTopology.links(player.level())) {
          if (observer.last != null && observer.last.inverse().equals(link)) continue;
          var entrance = link.entrance().endpoint();
          if (entrance.center().distanceToSqr(eye) > Math.pow((radius + 1) * 16.0, 2)) continue;
          arrivalCentres(centers, entrance);
          arrivalCentres(centers, link.exit().endpoint());
          var exit = link.exit().endpoint().center();
          simulationCenters.add(
              ChunkPos.pack((int) Math.floor(exit.x) >> 4, (int) Math.floor(exit.z) >> 4));

          var transform = link.transform();
          var virtualEye = transform.point(eye);
          observers.add(new PlayerPortalVisibility.Observer(
              virtualEye, link.exit().endpoint(), arriving(entrance, eye, observer.velocity)));
          if (!positions.add(virtualEye)) continue;
          next.add(new Observer(virtualEye, transform.vector(observer.velocity), link));
          var center =
              new ChunkPos(
                  (int) Math.floor(virtualEye.x) >> 4, (int) Math.floor(virtualEye.z) >> 4);
          centers.add(center.pack());
        }
      }
      frontier = next;
    }
    state.retainLinks(PortalTopology.links(player.level()));
    boolean viewsChanged = state.viewRadius() != radius || !waiting.centers.equals(centers)
        || !waiting.preparationCenters.equals(preparationCenters) || !Objects.equals(waiting.retained, retained);
    boolean simulationChanged = !waiting.simulationCenters.equals(simulationCenters);
    if (!viewsChanged && !simulationChanged) {
      state.observe(previous, observers, state.simulationChunks(), radius);
      return;
    }
    waiting.centers = centers;
    waiting.simulationCenters = simulationCenters;
    waiting.preparationCenters = preparationCenters;
    waiting.retained = retained;
    var chunks = previous;
    if (viewsChanged) {
      chunks = ChunkViewUnion.collect(centers, radius);
      if (retained != null) retained.addChunks(chunks);
      for (long center : preparationCenters) addCore(chunks, center);
      for (long center : simulationCenters) addCore(chunks, center);
    }
    var simulation = state.simulationChunks();
    if (simulationChanged) {
      simulation = new LongOpenHashSet();
      for (long center : simulationCenters) addCore(simulation, center);
    }
    state.observe(chunks, observers, simulation, radius);
    if (!viewsChanged) return;
    waiting.chunks.retainAll(chunks);
    for (long key : previous) {
      var pos = ChunkPos.unpack(key);
      if (!handoffs.containsKey(player.getUUID())
          && !chunks.contains(key)
          && !player.getChunkTrackingView().contains(pos))
        ChunkMapAccessor.portalix$dropChunk(player, pos);
    }
    for (long key : chunks) {
      if (!((ChunkDeliveryAccess) player.connection.chunkSender)
              .portalix$retain(player.level(), key)
          && !player.connection.chunkSender.isPending(key)) waiting.chunks.add(key);
    }
  }

  private static long chunkPosition(PortalEndpoint portal) {
    var point = portal.center();
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
      update(player, radius);
      return;
    }
    if (player.getChunkTrackingView() instanceof ChunkTrackingView.Positioned current
        && current.center().equals(player.chunkPosition())
        && current.viewDistance() == viewRadius(player, radius)) {
      update(player, radius);
      return;
    }
    var waiting = pending.get(player.getUUID());
    var previous =
        waiting != null && waiting.level == player.level()
            ? PlayerPortalVisibility.of(player).chunks()
            : new LongOpenHashSet();
    handoffs.put(player.getUUID(), new Handoff(previous));
    update(player, radius);
  }

  public void moveFinished(ServerPlayer player) {
    var handoff = handoffs.get(player.getUUID());
    if (handoff == null || --handoff.depth != 0) return;
    handoffs.remove(player.getUUID());
    var remote = PlayerPortalVisibility.of(player).chunks();
    for (long key : handoff.previous) {
      var pos = ChunkPos.unpack(key);
      if (!remote.contains(key) && !player.getChunkTrackingView().contains(pos))
        ChunkMapAccessor.portalix$dropChunk(player, pos);
    }
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
    PlayerPortalVisibility.of(player).replace(new LongOpenHashSet(), List.of());
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
    final LongOpenHashSet chunks = new LongOpenHashSet();
    LongOpenHashSet centers = new LongOpenHashSet();
    LongOpenHashSet simulationCenters = new LongOpenHashSet();
    LongOpenHashSet preparationCenters = new LongOpenHashSet();
    @Nullable Claim retained;

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

  private record Observed(ServerLevel level, LongOpenHashSet chunks, LongOpenHashSet simulation) {}

  private static int viewRadius(ServerPlayer player, int serverRadius) {
    int requested = player.requestedViewDistance();
    return Math.max(2, requested > 0 ? Math.min(serverRadius, requested) : serverRadius);
  }

  private record Observer(Vec3 eye, Vec3 velocity, PortalLink last) {}
}
