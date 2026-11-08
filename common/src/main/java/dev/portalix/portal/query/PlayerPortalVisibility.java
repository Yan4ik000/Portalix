package dev.portalix.portal.query;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.PortalLink;
import dev.portalix.world.chunk.PortalChunkObservers;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

public final class PlayerPortalVisibility {
  private LongOpenHashSet chunks = new LongOpenHashSet();
  private List<Vec3> eyes = List.of();
  private List<Observer> observers = List.of();
  private int viewRadius;
  private LongOpenHashSet simulationChunks = new LongOpenHashSet();
  private PortalChunkObservers priorities;
  private ChunkTrackingView priorityView;
  private ChunkPos priorityCenter;
  private ServerLevel priorityLevel;
  private final Long2ObjectOpenHashMap<Ready> ready = new Long2ObjectOpenHashMap<>();

  public void ready(PortalLink link) {
    var exit = link.exit().endpoint().center();
    ready.put(link.entrance().identity(),
        new Ready(link.generation(), SectionPos.posToSectionCoord(exit.x), SectionPos.posToSectionCoord(exit.z)));
  }

  public boolean isReady(PortalLink link) {
    var known = ready.get(link.entrance().identity());
    return known != null && known.generation == link.generation();
  }

  public void retainLinks(List<PortalLink> links) {
    if (ready.isEmpty()) return;
    var live = new LongOpenHashSet();
    for (var link : links) if (isReady(link)) live.add(link.entrance().identity());
    ready.keySet().retainAll(live);
  }

  public void forgetColumn(long position) {
    if (ready.isEmpty()) return;
    int x = ChunkPos.getX(position), z = ChunkPos.getZ(position);
    ready.values().removeIf(known -> Math.abs(known.x - x) <= 1 && Math.abs(known.z - z) <= 1);
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

  public int viewRadius() {
    return viewRadius;
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

  public void replace(LongOpenHashSet chunks, List<Vec3> eyes) {
    replace(chunks, eyes, new LongOpenHashSet());
  }

  public void replace(LongOpenHashSet chunks, List<Vec3> eyes, LongOpenHashSet simulationChunks) {
    this.chunks = chunks;
    this.eyes = List.copyOf(eyes);
    this.observers = List.of();
    this.simulationChunks = simulationChunks;
    this.viewRadius = 0;
    this.priorities = null;
  }

  public void observe(
      LongOpenHashSet chunks, List<Observer> observers,
      LongOpenHashSet simulationChunks, int viewRadius) {
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
    if (this.viewRadius != viewRadius) this.priorities = null;
    this.simulationChunks = simulationChunks;
    this.viewRadius = viewRadius;
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

  public record Observer(Vec3 eye, PortalEndpoint exit, boolean arrival) {}

  private record Ready(long generation, int x, int z) {}
}
