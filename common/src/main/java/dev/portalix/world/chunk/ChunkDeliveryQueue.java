package dev.portalix.world.chunk;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.function.LongUnaryOperator;

public final class ChunkDeliveryQueue {
  private final LongOpenHashSet delivered = new LongOpenHashSet();
  private final LongOpenHashSet invalidated = new LongOpenHashSet();
  private final LongLinkedOpenHashSet retiring = new LongLinkedOpenHashSet();
  private boolean retirementTurn = true;

  public void clear() {
    delivered.clear();
    invalidated.clear();
    retiring.clear();
    retirementTurn = true;
  }

  public void sent(long chunk) {
    delivered.add(chunk);
    invalidated.remove(chunk);
    retiring.remove(chunk);
  }

  public boolean requested(long chunk) {
    retiring.remove(chunk);
    return delivered(chunk);
  }

  public boolean delivered(long chunk) {
    return delivered.contains(chunk) && !invalidated.contains(chunk);
  }

  public void invalidate(long chunk) {
    if (delivered.contains(chunk)) invalidated.add(chunk);
  }

  public void retire(long chunk) {
    if (delivered.contains(chunk)) retiring.add(chunk);
  }

  public boolean hasRetirements() {
    return !retiring.isEmpty();
  }

  public Batch select(long[] ready, int quota, LongUnaryOperator distance) {
    var forgotten = new LongArrayList();
    var loaded = new LongArrayList();
    int next = 0;
    while (forgotten.size() + loaded.size() < quota) {
      boolean hasLoad = next < ready.length;
      if (hasLoad
          && (retiring.isEmpty() || distance.applyAsLong(ready[next]) <= 2 || !retirementTurn)) {
        loaded.add(ready[next++]);
        retirementTurn = true;
      } else if (!retiring.isEmpty()) {
        long chunk = retiring.removeFirstLong();
        forgotten.add(chunk);
        delivered.remove(chunk);
        invalidated.remove(chunk);
        retirementTurn = false;
      } else break;
    }
    return new Batch(forgotten.toLongArray(), loaded.toLongArray());
  }

  public record Batch(long[] forgotten, long[] loaded) {
    public int size() {
      return forgotten.length + loaded.length;
    }
  }
}
