package dev.portalix.client.compat.sodium;

import it.unimi.dsi.fastutil.longs.AbstractLong2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.util.NoSuchElementException;

public final class ColumnSectionMap<T> extends AbstractLong2ReferenceMap<T> {
  private static final long serialVersionUID = 1L;
  public static final int HEIGHT = 32;
  private final Long2ReferenceOpenHashMap<Column> columns = new Long2ReferenceOpenHashMap<>();
  private int size;

  private static final class Column implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("serial")
    final Object[] values;

    int occupied;

    Column() {
      values = new Object[HEIGHT];
    }
  }

  public void ensureCapacity(int sections) {
    columns.ensureCapacity(sections / 16 + (sections % 16 == 0 ? 0 : 1));
  }

  public int removeColumn(long position) {
    var column = columns.remove(position & -HEIGHT);
    if (column == null) return 0;
    int removed = Integer.bitCount(column.occupied);
    size -= removed;
    return removed;
  }

  @Override
  @SuppressWarnings("unchecked")
  public T get(long key) {
    var column = columns.get(key & -HEIGHT);
    int index = (int) key & (HEIGHT - 1);
    return column == null || (column.occupied & (1 << index)) == 0
        ? defRetValue
        : (T) column.values[index];
  }

  @Override
  public boolean containsKey(long key) {
    var column = columns.get(key & -HEIGHT);
    return column != null && (column.occupied & (1 << ((int) key & (HEIGHT - 1)))) != 0;
  }

  @Override
  public boolean containsValue(Object value) {
    for (var column : columns.values()) {
      int occupied = column.occupied;
      while (occupied != 0) {
        int index = Integer.numberOfTrailingZeros(occupied);
        if (column.values[index] == value) return true;
        occupied &= occupied - 1;
      }
    }
    return false;
  }

  @Override
  @SuppressWarnings("unchecked")
  public T put(long key, T value) {
    long position = key & -HEIGHT;
    var column = columns.get(position);
    if (column == null) {
      column = new Column();
      columns.put(position, column);
    }
    int index = (int) key & (HEIGHT - 1), bit = 1 << index;
    T previous = defRetValue;
    if ((column.occupied & bit) == 0) {
      column.occupied |= bit;
      size++;
    } else previous = (T) column.values[index];
    column.values[index] = value;
    return previous;
  }

  @Override
  @SuppressWarnings("unchecked")
  public T remove(long key) {
    long position = key & -HEIGHT;
    var column = columns.get(position);
    int index = (int) key & (HEIGHT - 1), bit = 1 << index;
    if (column == null || (column.occupied & bit) == 0) return defRetValue;
    T previous = (T) column.values[index];
    column.values[index] = null;
    column.occupied &= ~bit;
    size--;
    if (column.occupied == 0) {
      columns.remove(position);
    }
    return previous;
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
  public ObjectSet<Entry<T>> long2ReferenceEntrySet() {
    return new Entries();
  }

  private final class Entries extends BasicEntrySet<T> implements FastEntrySet<T> {
    Entries() {
      super(ColumnSectionMap.this);
    }

    @Override
    public ObjectIterator<Entry<T>> iterator() {
      return new EntriesIterator(false);
    }

    @Override
    public ObjectIterator<Entry<T>> fastIterator() {
      return new EntriesIterator(true);
    }
  }

  private static final class MutableEntry<T> extends BasicEntry<T> {
    void update(long key, T value) {
      this.key = key;
      this.value = value;
    }
  }

  private final class EntriesIterator implements ObjectIterator<Entry<T>> {
    private final long[] positions = columns.keySet().toLongArray();
    private final MutableEntry<T> reusable;
    private int nextColumn;
    private long position;
    private Column column;
    private int occupied;
    private long lastKey;
    private boolean removable;

    EntriesIterator(boolean reuse) {
      reusable = reuse ? new MutableEntry<>() : null;
    }

    @Override
    public boolean hasNext() {
      if (column != null) occupied &= column.occupied;
      while (occupied == 0 && nextColumn < positions.length) {
        position = positions[nextColumn++];
        column = columns.get(position);
        occupied = column == null ? 0 : column.occupied;
      }
      return occupied != 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Entry<T> next() {
      if (!hasNext()) throw new NoSuchElementException();
      int index = Integer.numberOfTrailingZeros(occupied);
      occupied &= occupied - 1;
      lastKey = position | index;
      removable = true;
      T value = (T) column.values[index];
      if (reusable == null) return new BasicEntry<>(lastKey, value);
      reusable.update(lastKey, value);
      return reusable;
    }

    @Override
    public void remove() {
      if (!removable) throw new IllegalStateException();
      ColumnSectionMap.this.remove(lastKey);
      removable = false;
    }
  }
}
