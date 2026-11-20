package dev.portalix.server.portal;

import dev.portalix.portal.PortalViewLimits;
import dev.portalix.portal.PortalViewTransition;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;

/** Already delivered terrain kept available for another crossing, independent of portal identities. */
final class PortalWarmTerrain {
  private static final int RELEASES_PER_TICK = (int) Math.round(16 / PortalViewTransition.SLOWDOWN);
  private final LongLinkedOpenHashSet retiring = new LongLinkedOpenHashSet();
  private long lastRelease = Long.MIN_VALUE;
  private final Long2ObjectOpenHashMap<Departure> columns = new Long2ObjectOpenHashMap<>();
  private final ArrayList<Departure> departures = new ArrayList<>();

  boolean contains(long column) {
    return columns.containsKey(column);
  }

  void crossed(ChunkPos source, ChunkPos target, ChunkTrackingView primary, int limit, long tick,
      LongSet delivered, LongPredicate current, LongConsumer acquire) {
    // On the first distant crossing, every acquired column belongs to the disjoint departure view.
    boolean revisit = !columns.isEmpty() || source.getChessboardDistance(target) <= 2 * (limit + 1);
    capture(source, primary, limit, tick, delivered, current, acquire);
    if (revisit && !source.equals(target) && !columns.isEmpty())
      capture(target, ChunkTrackingView.EMPTY, limit, tick, columns.keySet(), current, acquire);
  }

  private void capture(ChunkPos center, ChunkTrackingView primary, int limit, long tick, LongSet delivered,
      LongPredicate current, LongConsumer acquire) {
    Departure departure = null;
    for (var existing : departures)
      if (existing.center.equals(center)) {
        departure = existing;
        break;
      }
    if (departure == null) departure = new Departure(center);
    departure.tick = tick;
    departure.radius = departure.remaining;
    // A chain of crossings shares the capacity of one extra primary view.
    int width = 2 * limit + 3;
    int preview = PortalViewLimits.preview(limit);
    var keys = delivered.iterator();
    while (keys.hasNext()) {
      long key = keys.nextLong();
      int x = ChunkPos.getX(key), z = ChunkPos.getZ(key);
      if (!ChunkTrackingView.isWithinDistance(center.x(), center.z(), limit, x, z, true)) continue;
      var previous = columns.get(key);
      if (previous == null && (columns.size() >= width * width || !primary.contains(x, z)
          && !ChunkTrackingView.isWithinDistance(center.x(), center.z(), preview, x, z, true))) continue;
      if (!current.test(key)) continue;
      if (previous == null) acquire.accept(key);
      else retiring.remove(key);
      if (previous != departure) {
        if (previous != null) previous.columns.remove(key);
        columns.put(key, departure);
      }
      departure.columns.add(key);
      while (!ChunkTrackingView.isWithinDistance(center.x(), center.z(), departure.radius, x, z, true))
        departure.radius++;
    }
    departure.remaining = departure.radius;
    if (!departure.columns.isEmpty() && !departures.contains(departure)) departures.add(departure);
    departures.removeIf(old -> old.columns.isEmpty());
  }

  void advance(long tick, int limit, LongPredicate canRelease, LongConsumer release) {
    if (columns.isEmpty()) return;
    var views = departures.iterator();
    while (views.hasNext()) {
      var departure = views.next();
      long elapsed = tick - departure.tick;
      int radius = Math.min(departure.radius, (int) Math.ceil(
          PortalViewTransition.retainedRadius(departure.radius, elapsed, limit)));
      if (elapsed >= PortalViewTransition.LOAD_DELAY_TICKS && radius <= PortalViewLimits.preview(limit))
        radius = 0;
      if (radius >= departure.remaining) continue;
      departure.remaining = radius;
      var remaining = departure.columns.iterator();
      while (remaining.hasNext()) {
        long key = remaining.nextLong();
        if (radius > 0 && ChunkTrackingView.isWithinDistance(departure.center.x(), departure.center.z(),
            radius, ChunkPos.getX(key), ChunkPos.getZ(key), true)) continue;
        remaining.remove();
        retiring.add(key);
      }
      if (departure.columns.isEmpty()) views.remove();
    }
    if (tick == lastRelease || retiring.isEmpty()) return;
    lastRelease = tick;
    int budget = Math.min(RELEASES_PER_TICK, retiring.size());
    for (int i = 0; i < budget; i++) {
      long key = retiring.removeFirstLong();
      if (!canRelease.test(key)) {
        retiring.add(key);
        continue;
      }
      columns.remove(key);
      release.accept(key);
    }
  }

  void clear(LongConsumer release) {
    var keys = columns.keySet().iterator();
    while (keys.hasNext()) {
      long key = keys.nextLong();
      keys.remove();
      release.accept(key);
    }
    retiring.clear();
    departures.clear();
  }

  private static final class Departure {
    final ChunkPos center;
    long tick;
    final LongOpenHashSet columns = new LongOpenHashSet();
    int radius;
    int remaining;

    Departure(ChunkPos center) {
      this.center = center;
    }
  }
}
