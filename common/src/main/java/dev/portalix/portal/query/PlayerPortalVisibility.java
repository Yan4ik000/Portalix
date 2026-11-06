package dev.portalix.portal.query;

import dev.portalix.portal.PortalLink;
import dev.portalix.api.PortalEndpoint;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

public final class PlayerPortalVisibility {
  private LongOpenHashSet chunks = new LongOpenHashSet();
  private List<Vec3> eyes = List.of();
  private List<Observer> observers = List.of();
  private int viewRadius;
  private LongOpenHashSet simulationChunks = new LongOpenHashSet();
  private final Map<Long, Long> ready = new HashMap<>();

  public void ready(PortalLink link) {
    ready.put(link.entrance().identity(), link.generation());
  }

  public boolean isReady(PortalLink link) {
    return ready.getOrDefault(link.entrance().identity(), -1L) == link.generation();
  }

  public void retainLinks(List<PortalLink> links) {
    ready
        .entrySet()
        .removeIf(
            entry ->
                links.stream()
                    .noneMatch(
                        link ->
                            link.entrance().identity() == entry.getKey()
                                && link.generation() == entry.getValue()));
  }

  public static PlayerPortalVisibility of(ServerPlayer player) {
    return ((Access) player).portalix$observation();
  }

  public boolean contains(int x, int z) {
    return chunks.contains(ChunkPos.pack(x, z));
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
  }

  public void observe(
      LongOpenHashSet chunks, List<Observer> observers,
      LongOpenHashSet simulationChunks, int viewRadius) {
    this.chunks = chunks;
    this.observers = List.copyOf(observers);
    this.eyes = observers.stream().map(Observer::eye).distinct().toList();
    this.simulationChunks = simulationChunks;
    this.viewRadius = viewRadius;
  }

  public Vec3 nearestDelta(Vec3 source, Vec3 direct) {
    var result = direct;
    double distance = direct.x * direct.x + direct.z * direct.z;
    for (var eye : eyes) {
      var delta = eye.subtract(source);
      double candidate = delta.x * delta.x + delta.z * delta.z;
      if (candidate < distance) {
        result = delta;
        distance = candidate;
      }
    }
    return result;
  }

  public interface Access {
    PlayerPortalVisibility portalix$observation();
  }

  public record Observer(Vec3 eye, PortalEndpoint exit, boolean arrival) {}
}
