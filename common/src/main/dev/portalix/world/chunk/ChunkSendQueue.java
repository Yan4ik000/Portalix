package dev.portalix.world.chunk;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Arrays;
import java.util.function.LongPredicate;
import java.util.function.LongUnaryOperator;

public final class ChunkSendQueue {
  private ChunkSendQueue() {}

  public static long[] nearest(
      LongSet pending, int count, LongUnaryOperator distance, LongPredicate ready) {
    int capacity = Math.min(count, pending.size());
    if (capacity <= 0) return new long[0];
    long[] chunks = new long[capacity];
    long[] distances = new long[capacity];
    int size = 0;
    for (long chunk : pending) {
      long rank = distance.applyAsLong(chunk);
      if (size == capacity && compare(rank, chunk, distances[0], chunks[0]) >= 0) continue;
      if (!ready.test(chunk)) continue;
      if (size < capacity) {
        int index = size++;
        while (index > 0) {
          int parent = (index - 1) >>> 1;
          if (compare(rank, chunk, distances[parent], chunks[parent]) <= 0) break;
          chunks[index] = chunks[parent];
          distances[index] = distances[parent];
          index = parent;
        }
        chunks[index] = chunk;
        distances[index] = rank;
      } else {
        replaceRoot(chunks, distances, size, chunk, rank);
      }
    }
    for (int end = size - 1; end > 0; end--) {
      long chunk = chunks[end], rank = distances[end];
      chunks[end] = chunks[0];
      replaceRoot(chunks, distances, end, chunk, rank);
    }
    return size == chunks.length ? chunks : Arrays.copyOf(chunks, size);
  }

  private static void replaceRoot(
      long[] chunks, long[] distances, int size, long chunk, long rank) {
    int index = 0;
    while (index * 2 + 1 < size) {
      int child = index * 2 + 1;
      if (child + 1 < size
          && compare(distances[child + 1], chunks[child + 1], distances[child], chunks[child]) > 0)
        child++;
      if (compare(rank, chunk, distances[child], chunks[child]) >= 0) break;
      chunks[index] = chunks[child];
      distances[index] = distances[child];
      index = child;
    }
    chunks[index] = chunk;
    distances[index] = rank;
  }

  private static int compare(long aDistance, long a, long bDistance, long b) {
    int order = Long.compare(aDistance, bDistance);
    return order != 0 ? order : Long.compare(a, b);
  }
}
