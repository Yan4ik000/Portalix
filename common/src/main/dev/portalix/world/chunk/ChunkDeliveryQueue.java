package dev.portalix.world.chunk;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.function.LongUnaryOperator;

public final class ChunkDeliveryQueue {
  private final LongOpenHashSet delivered = new LongOpenHashSet();
  private final LongSet present = LongSets.unmodifiable(delivered);
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
    retiring.remove(chunk);
    invalidated.remove(chunk);
  }

  public boolean requested(long chunk) {
    retiring.remove(chunk);
    return delivered(chunk);
  }

  public boolean delivered(long chunk) {
    return delivered.contains(chunk) && !invalidated.contains(chunk);
  }

  public boolean watching(long chunk) {
    return subscribed(chunk) && !invalidated.contains(chunk);
  }

  public boolean subscribed(long chunk) {
    return delivered.contains(chunk) && !retiring.contains(chunk);
  }

  public LongSet presentColumns() {
    return present;
  }

  public void invalidate(long chunk) {
    if (delivered.contains(chunk)) invalidated.add(chunk);
  }

  public void retire(long chunk) {
    if (delivered.contains(chunk)) retiring.add(chunk);
  }

  public boolean retiring(long chunk) {
    return retiring.contains(chunk);
  }

  public boolean hasRetirements() {
    return !retiring.isEmpty();
  }

  public Batch select(long[] ready, int quota, int retirementLimit, LongUnaryOperator distance) {
    var forgotten = new LongArrayList();
    var loaded = new LongArrayList();
    int next = 0;
    while (forgotten.size() + loaded.size() < quota) {
      boolean hasLoad = next < ready.length;
      boolean canRetire = !retiring.isEmpty() && forgotten.size() < retirementLimit;
      if (hasLoad
          && (!canRetire || distance.applyAsLong(ready[next]) <= 2 || !retirementTurn)) {
        loaded.add(ready[next++]);
        retirementTurn = true;
      } else if (canRetire) {
        forgotten.add(retireNext());
      } else break;
    }
    return new Batch(forgotten.toLongArray(), loaded.toLongArray());
  }

  public long[] drainRetirements(int limit) {
    var forgotten = new long[Math.min(retiring.size(), limit)];
    for (int i = 0; i < forgotten.length; i++) forgotten[i] = retireNext();
    return forgotten;
  }

  private long retireNext() {
    long chunk = retiring.removeFirstLong();
    delivered.remove(chunk);
    invalidated.remove(chunk);
    retirementTurn = false;
    return chunk;
  }

  public record Batch(long[] forgotten, long[] loaded) {}
}
