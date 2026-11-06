package dev.portalix.world.chunk;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.List;
import java.util.function.LongToIntFunction;
import org.jspecify.annotations.Nullable;

public final class PortalChunkTicketOrder {
  private static final int MAX_PRIORITY_BURST = 7;
  private LongLinkedOpenHashSet[][] positions;
  private LongToIntFunction priority;
  private int priorityBurst;
  private boolean portalTurn;
  private boolean dirty = true;

  public PortalChunkTicketOrder(LongToIntFunction priority) {
    this.priority = priority;
  }

  public void prioritize(LongToIntFunction priority) {
    this.priority = priority;
    dirty = true;
  }

  public @Nullable Selection next(
      List<Long2ObjectLinkedOpenHashMap<List<Runnable>>> queues, int firstLevel) {
    if (firstLevel >= queues.size()) return null;
    if (dirty) rebuild(queues);
    // Regular native grants preserve FIFO progress despite a continuous stream of urgent portal work.
    if (priorityBurst == MAX_PRIORITY_BURST) {
      priorityBurst = 0;
      return new Selection(firstLevel, queues.get(firstLevel).firstLongKey());
    }
    priorityBurst++;
    var core = first(0, firstLevel);
    if (core != null) return core;
    var primary = first(1, firstLevel);
    var aperture = first(2, firstLevel);
    if (primary != null && aperture != null) {
      portalTurn = !portalTurn;
      return portalTurn ? aperture : primary;
    }
    if (primary != null) return primary;
    if (aperture != null) return aperture;
    for (int tier = 3; tier < 5; tier++) {
      var selection = first(tier, firstLevel);
      if (selection != null) return selection;
    }
    throw new IllegalStateException("Portal ticket index is missing queued work");
  }

  private @Nullable Selection first(int tier, int firstLevel) {
    for (int level = firstLevel; level < positions.length; level++) {
      var queue = positions[level][tier];
      if (queue != null && !queue.isEmpty()) return new Selection(level, queue.firstLong());
    }
    return null;
  }

  private void rebuild(List<Long2ObjectLinkedOpenHashMap<List<Runnable>>> queues) {
    if (positions == null) positions = new LongLinkedOpenHashSet[queues.size()][5];
    for (var level : positions) for (var tier : level) if (tier != null) tier.clear();
    dirty = false;
    for (int level = 0; level < queues.size(); level++) {
      var iterator = queues.get(level).keySet().iterator();
      while (iterator.hasNext()) submitted(level, iterator.nextLong());
    }
  }

  public void submitted(int level, long position) {
    if (dirty) return;
    var priorities = positions[level];
    for (var queue : priorities) if (queue != null && queue.contains(position)) return;
    int tier = priority.applyAsInt(position);
    if (priorities[tier] == null) priorities[tier] = new LongLinkedOpenHashSet();
    priorities[tier].add(position);
  }

  public void retired(int level, long position) {
    if (dirty) return;
    for (var queue : positions[level]) if (queue != null) queue.remove(position);
  }

  public record Selection(int level, long position) {}

  public interface Access {
    void portalix$prioritize(@Nullable LongToIntFunction priority);
  }
}
