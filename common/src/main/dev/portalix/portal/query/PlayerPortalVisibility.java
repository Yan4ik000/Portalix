package dev.portalix.portal.query;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.network.portal.PortalViewStatusPayload;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalViewLimits;
import dev.portalix.portal.PortalViewTransition;
import dev.portalix.world.chunk.PortalChunkObservers;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongPredicate;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

public final class PlayerPortalVisibility {
  private LongOpenHashSet chunks = new LongOpenHashSet();
  private List<Vec3> eyes = List.of();
  private List<Observer> observers = List.of();
  private LongOpenHashSet simulationChunks = new LongOpenHashSet();
  private PortalChunkObservers priorities;
  private ChunkTrackingView priorityView;
  private ChunkPos priorityCenter;
  private ServerLevel priorityLevel;
  private final Long2ObjectOpenHashMap<Ready> ready = new Long2ObjectOpenHashMap<>();

  private long resumeLoadingAt;
  private int arrivalRadius;

  public void portalCrossed(long tick, int radius) {
    arrivalRadius = radius;
    resumeLoadingAt = tick + PortalViewTransition.LOAD_DELAY_TICKS;
  }

  public long resumeLoadingAt() {
    return resumeLoadingAt;
  }

  public void resumeLoading() {
    resumeLoadingAt = 0;
  }

  public boolean delayingLoading(long tick) {
    return tick < resumeLoadingAt;
  }

  public boolean loadingTransitionActive(long tick) {
    return resumeLoadingAt != 0 && tick - resumeLoadingAt < PortalViewTransition.LOAD_RAMP_TICKS;
  }

  public int loadingRadius(long tick, int playerDistance, int limit) {
    return resumeLoadingAt == 0 ? limit : PortalViewTransition.loadingRadius(
        tick - resumeLoadingAt, Math.min(arrivalRadius, Math.min(playerDistance, limit)), limit);
  }

  public int trackingDistance(long tick, int distance) {
    return loadingRadius(tick, distance, distance);
  }

  public void ready(PortalLink link) {
    acknowledge(link, true);
  }

  public void acknowledge(PortalLink link, boolean confirmed) {
    var exit = PortalViewLimits.frontColumn(link.exit().endpoint());
    ready.put(link.entrance().identity(), new Ready(link.generation(), exit, confirmed));
  }

  public boolean isReady(PortalLink link) {
    var known = ready.get(link.entrance().identity());
    return known != null && known.generation == link.generation() && known.confirmed;
  }

  public boolean hasReadyLinks() {
    for (var known : ready.values()) if (known.confirmed) return true;
    return false;
  }

  public void retainLinks(List<PortalLink> links) {
    if (ready.isEmpty()) return;
    var live = new LongOpenHashSet();
    for (var link : links) {
      var known = ready.get(link.entrance().identity());
      if (known != null && known.generation == link.generation()) live.add(link.entrance().identity());
    }
    ready.keySet().retainAll(live);
  }

  public void suspendColumn(long position, Consumer<PortalViewStatusPayload> update) {
    var entries = ready.long2ObjectEntrySet().fastIterator();
    while (entries.hasNext()) {
      var entry = entries.next();
      var known = entry.getValue();
      if (!known.confirmed || !known.contains(position)) continue;
      known.confirmed = false;
      update.accept(new PortalViewStatusPayload(entry.getLongKey(), known.generation, false));
    }
  }

  public void forgetColumn(long position) {
    ready.values().removeIf(known -> known.contains(position));
  }

  public boolean confirmViews(LongPredicate received, Consumer<PortalViewStatusPayload> update) {
    boolean changed = false;
    var entries = ready.long2ObjectEntrySet().fastIterator();
    while (entries.hasNext()) {
      var entry = entries.next();
      var known = entry.getValue();
      if (!known.confirmed && PortalViewLimits.hasExitCore(known.center, received)) {
        known.confirmed = true;
        changed = true;
        update.accept(new PortalViewStatusPayload(entry.getLongKey(), known.generation, true));
      }
    }
    return changed;
  }

  public void clearReady() {
    ready.clear();
  }

  public static PlayerPortalVisibility of(ServerPlayer player) {
    return ((Access) player).portalix$observation();
  }

  public boolean contains(int x, int z) {
    return contains(ChunkPos.pack(x, z));
  }

  public boolean contains(long chunk) {
    return chunks.contains(chunk);
  }

  public LongOpenHashSet chunks() {
    return chunks;
  }

  public List<Vec3> eyes() {
    return eyes;
  }

  public List<Observer> observers() {
    return observers;
  }

  public PortalChunkObservers chunkPriorities(ServerPlayer player) {
    var view = player.getChunkTrackingView();
    var center = player.chunkPosition();
    var level = player.level();
    if (priorities == null || priorityLevel != level || !view.equals(priorityView)
        || !center.equals(priorityCenter)) {
      priorities = PortalChunkObservers.create(player, this);
      priorityView = view;
      priorityCenter = center;
      priorityLevel = level;
    }
    return priorities;
  }

  public LongOpenHashSet simulationChunks() {
    return simulationChunks;
  }

  public void resetObservation(LongOpenHashSet chunks) {
    resumeLoading();
    this.chunks = chunks;
    this.eyes = List.of();
    this.observers = List.of();
    this.simulationChunks = new LongOpenHashSet();
    this.priorities = null;
  }

  public void observe(
      LongOpenHashSet chunks, List<Observer> observers, LongOpenHashSet simulationChunks) {
    this.chunks = chunks;
    if (this.observers != observers) {
      this.observers = List.copyOf(observers);
      if (observers.isEmpty()) {
        this.eyes = List.of();
      } else {
        var positions = new LinkedHashSet<Vec3>();
        for (var observer : observers) positions.add(observer.eye);
        this.eyes = List.copyOf(positions);
      }
      this.priorities = null;
    }
    this.simulationChunks = simulationChunks;
  }

  public boolean eyeWithinHorizontalRange(Vec3 source, double rangeSquared) {
    for (var eye : eyes) {
      double x = eye.x - source.x, z = eye.z - source.z;
      if (x * x + z * z <= rangeSquared) return true;
    }
    return false;
  }

  public interface Access {
    PlayerPortalVisibility portalix$observation();
  }

  public record Observer(Vec3 eye, PortalEndpoint exit, boolean arrival, int radius) {}

  private static final class Ready {
    final long generation;
    final ChunkPos center;
    boolean confirmed;

    Ready(long generation, ChunkPos center, boolean confirmed) {
      this.generation = generation;
      this.center = center;
      this.confirmed = confirmed;
    }

    boolean contains(long position) {
      return center.getChessboardDistance(ChunkPos.getX(position), ChunkPos.getZ(position))
          <= PortalViewLimits.CORE_RADIUS;
    }
  }
}
