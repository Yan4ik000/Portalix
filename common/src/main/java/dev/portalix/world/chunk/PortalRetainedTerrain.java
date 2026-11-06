package dev.portalix.world.chunk;

import dev.portalix.api.PortalEndpoint;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongPredicate;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

public final class PortalRetainedTerrain {
  private record View(PortalEndpoint endpoint, ChunkTrackingView.Positioned captured, int capacity, int radius,
      LongPredicate resident) {}

  private final Map<UUID, View> views = new HashMap<>();
  private final int loadingHalo;
  private boolean dirty = true;
  private int serverRadius = -1;
  private int sharedRadius;

  public PortalRetainedTerrain(int loadingHalo) {
    this.loadingHalo = loadingHalo;
  }

  public void observe(UUID player, @Nullable PortalEndpoint endpoint, ChunkTrackingView primary, int radius,
      LongPredicate resident) {
    var previous = views.get(player);
    if (previous != null && !previous.endpoint.equals(endpoint)) {
      release(player);
      previous = null;
    }
    if (previous == null && endpoint != null && primary instanceof ChunkTrackingView.Positioned captured) {
      var center = endpoint.center();
      if (captured.contains((int) Math.floor(center.x) >> 4, (int) Math.floor(center.z) >> 4)) {
        int capacity = captureRadius(endpoint, captured, Math.min(radius, captured.viewDistance()), resident);
        views.put(player, new View(endpoint, captured, capacity, capacity, resident));
        dirty = true;
      }
    } else if (previous != null) {
      int requested = Math.min(radius, previous.capacity);
      if (previous.radius != requested) {
        views.put(player, new View(previous.endpoint, previous.captured, previous.capacity, requested, previous.resident));
        dirty = true;
      }
    }
  }

  public @Nullable Claim claim(UUID player, int radius) {
    var view = views.get(player);
    if (view == null) return null;
    if (dirty || serverRadius != radius) {
      allocate(radius);
      view = views.get(player);
    }
    int granted = Math.min(sharedRadius, view.radius);
    if (granted == 0) return null;
    return new Claim(view.captured, center(view.endpoint, view.captured, granted), granted);
  }

  private int captureRadius(PortalEndpoint endpoint, ChunkTrackingView.Positioned captured, int radius,
      LongPredicate resident) {
    int minX = 1, maxX = 0, minZ = 1, maxZ = 0;
    for (int candidate = 1; candidate <= radius; candidate++) {
      var center = center(endpoint, captured, candidate);
      int extent = candidate + loadingHalo;
      // The weighted native source also loads a square FULL halo. Capture only a ready footprint.
      int nextMinX = center.x() - extent, nextMaxX = center.x() + extent;
      int nextMinZ = center.z() - extent, nextMaxZ = center.z() + extent;
      // Clamping the center makes successive squares nested; check only their new rows and columns.
      for (int x = nextMinX; x <= nextMaxX; x++) {
        if (x < minX || x > maxX) {
          if (!residentRow(resident, x, nextMinZ, nextMaxZ)) return candidate - 1;
        } else if (!residentRow(resident, x, nextMinZ, minZ - 1)
            || !residentRow(resident, x, maxZ + 1, nextMaxZ)) return candidate - 1;
      }
      minX = nextMinX;
      maxX = nextMaxX;
      minZ = nextMinZ;
      maxZ = nextMaxZ;
    }
    return radius;
  }

  private static boolean residentRow(LongPredicate resident, int x, int minZ, int maxZ) {
    for (int z = minZ; z <= maxZ; z++)
      if (!resident.test(ChunkPos.pack(x, z))) return false;
    return true;
  }

  private static ChunkPos center(PortalEndpoint endpoint, ChunkTrackingView.Positioned captured, int radius) {
    var point = endpoint.center();
    var original = captured.center();
    int margin = captured.viewDistance() - radius;
    return new ChunkPos(
        Math.clamp((int) Math.floor(point.x) >> 4, original.x() - margin, original.x() + margin),
        Math.clamp((int) Math.floor(point.z) >> 4, original.z() - margin, original.z() + margin));
  }

  private void allocate(int radius) {
    serverRadius = radius;
    dirty = false;
    sharedRadius = 0;
    // A smaller share may have let the outer captured terrain unload before this redistribution.
    for (var entry : views.entrySet()) {
      var view = entry.getValue();
      int capacity = captureRadius(view.endpoint, view.captured, view.capacity, view.resident);
      if (capacity != view.capacity)
        entry.setValue(new View(view.endpoint, view.captured, capacity, Math.min(capacity, view.radius), view.resident));
    }
    // Optional first-endpoint caches together may hold one extra native server view, including its FULL halo.
    long budget = columns(radius);
    for (int candidate = 1; candidate <= radius; candidate++) {
      long requested = 0;
      for (var view : views.values())
        if (view.radius > 0) requested += columns(Math.min(candidate, view.radius));
      if (requested > budget) break;
      sharedRadius = candidate;
    }
  }

  private long columns(int radius) {
    long width = 2L * (radius + loadingHalo) + 1;
    return width * width;
  }

  public void release(UUID player) {
    if (views.remove(player) != null) dirty = true;
  }

  public void retainPlayers(Set<UUID> players) {
    if (views.keySet().retainAll(players)) dirty = true;
  }

  public void clear() {
    views.clear();
    dirty = true;
  }

  public record Claim(ChunkTrackingView.Positioned captured, ChunkPos center, int radius) {
    public void addChunks(LongOpenHashSet chunks) {
      ChunkTrackingView.of(center, radius).forEach(position -> {
        if (captured.contains(position)) chunks.add(position.pack());
      });
    }
  }
}
