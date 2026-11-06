package dev.portalix.world.snapshot;

import it.unimi.dsi.fastutil.longs.AbstractLongSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import java.util.NoSuchElementException;
import net.minecraft.core.SectionPos;

public final class SectionLightUpdates extends AbstractLongSet {
  private final Long2IntOpenHashMap columns = new Long2IntOpenHashMap();
  private int size;

  @Override
  public boolean add(long section) {
    return add(section & -32L, 1 << ((int) section & 31));
  }

  private boolean add(long column, int mask) {
    int previous = columns.get(column);
    int next = previous | mask;
    if (next == previous) return false;
    columns.put(column, next);
    size += Integer.bitCount(next) - Integer.bitCount(previous);
    return true;
  }

  public void addNeighbors(long section) {
    int x = SectionPos.x(section), y = SectionPos.y(section), z = SectionPos.z(section);
    int index = y & 31;
    for (int dz = -1; dz <= 1; dz++) {
      for (int dx = -1; dx <= 1; dx++) {
        long column = SectionPos.asLong(x + dx, y, z + dz) & -32L;
        if (index == 0) {
          add(column, 3);
          add(SectionPos.asLong(x + dx, y - 1, z + dz) & -32L, Integer.MIN_VALUE);
        } else if (index == 31) {
          add(column, 3 << 30);
          add(SectionPos.asLong(x + dx, y + 1, z + dz) & -32L, 1);
        } else add(column, 7 << (index - 1));
      }
    }
  }

  @Override
  public boolean contains(long section) {
    return (columns.get(section & -32L) & (1 << ((int) section & 31))) != 0;
  }

  @Override
  public boolean remove(long section) {
    long column = section & -32L;
    int previous = columns.get(column);
    int next = previous & ~(1 << ((int) section & 31));
    if (next == previous) return false;
    if (next == 0) columns.remove(column);
    else columns.put(column, next);
    size--;
    return true;
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public void clear() {
    columns.clear();
    size = 0;
  }

  @Override
  public LongIterator iterator() {
    var entries = columns.long2IntEntrySet().fastIterator();
    return new LongIterator() {
      private long column;
      private int remaining;
      private int last;
      private boolean removable;

      @Override
      public boolean hasNext() {
        return remaining != 0 || entries.hasNext();
      }

      @Override
      public long nextLong() {
        if (remaining == 0) {
          if (!entries.hasNext()) throw new NoSuchElementException();
          var entry = entries.next();
          column = entry.getLongKey();
          remaining = entry.getIntValue();
        }
        last = Integer.lowestOneBit(remaining);
        remaining &= ~last;
        removable = true;
        return column | Integer.numberOfTrailingZeros(last);
      }

      @Override
      public void remove() {
        if (!removable) throw new IllegalStateException();
        int next = columns.get(column) & ~last;
        if (next == 0) entries.remove();
        else columns.put(column, next);
        size--;
        removable = false;
      }
    };
  }
}
