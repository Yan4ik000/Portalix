package dev.portalix.client.world;

import it.unimi.dsi.fastutil.HashCommon;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceArray;

public final class RetainedChunkStorage<T> {
  private volatile AtomicReferenceArray<Entry<T>> buckets = new AtomicReferenceArray<>(16);
  private volatile int size;

  private static final class Entry<T> {
    final long key;
    final Entry<T> next;
    volatile T value;

    Entry(long key, T value, Entry<T> next) {
      this.key = key;
      this.value = value;
      this.next = next;
    }
  }

  private static int bucket(long key, int capacity) {
    return (int) HashCommon.mix(key) & (capacity - 1);
  }

  public T get(long key) {
    var table = buckets;
    for (var entry = table.get(bucket(key, table.length())); entry != null; entry = entry.next)
      if (entry.key == key) return entry.value;
    return null;
  }

  public synchronized void put(long key, T value) {
    Objects.requireNonNull(value);
    var table = buckets;
    int index = bucket(key, table.length());
    for (var entry = table.get(index); entry != null; entry = entry.next) {
      if (entry.key == key) {
        entry.value = value;
        return;
      }
    }
    if (size >= table.length() - table.length() / 4) {
      var grown = new AtomicReferenceArray<Entry<T>>(Math.multiplyExact(table.length(), 2));
      for (int i = 0; i < table.length(); i++) {
        for (var entry = table.get(i); entry != null; entry = entry.next) {
          int target = bucket(entry.key, grown.length());
          grown.set(target, new Entry<>(entry.key, entry.value, grown.get(target)));
        }
      }
      table = grown;
      buckets = table;
      index = bucket(key, table.length());
    }
    table.set(index, new Entry<>(key, value, table.get(index)));
    size++;
  }

  public synchronized T remove(long key) {
    var table = buckets;
    int index = bucket(key, table.length());
    var head = table.get(index);
    T removed = null;
    for (var entry = head; entry != null; entry = entry.next)
      if (entry.key == key) {
        removed = entry.value;
        break;
      }
    if (removed == null) return null;
    Entry<T> retained = null;
    for (var entry = head; entry != null; entry = entry.next)
      if (entry.key != key) retained = new Entry<>(entry.key, entry.value, retained);
    table.set(index, retained);
    size--;
    return removed;
  }

  public int size() {
    return size;
  }

  public synchronized List<T> values() {
    var result = new ArrayList<T>(size);
    var table = buckets;
    for (int i = 0; i < table.length(); i++)
      for (var entry = table.get(i); entry != null; entry = entry.next) result.add(entry.value);
    return List.copyOf(result);
  }
}
