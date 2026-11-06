package dev.portalix.client.portal.terrain;

import dev.portalix.client.world.RetainedChunkStorage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;
import java.util.function.LongPredicate;
import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/** World coordinates own section lifetime; moving a camera never repurposes a live section. */
public final class PortalRenderSections<T extends RotatingSectionStorage.Value>
    extends RotatingSectionStorage<T> {
  private final int radius;
  private final int minY;
  private final int maxY;
  private final ValueCreator<T> factory;
  private final LongPredicate loaded;
  private final RetainedChunkStorage<AtomicReferenceArray<T>> columns =
      new RetainedChunkStorage<>();
  private volatile int count;
  private int nextIndex;
  private volatile SectionPos center;
  private List<T> snapshot;

  public PortalRenderSections(
      int radius,
      int minY,
      int maxY,
      ValueCreator<T> factory,
      SectionPos center,
      LongPredicate loaded) {
    super(0, 0, -1, factory);
    this.radius = radius;
    this.minY = minY;
    this.maxY = maxY;
    this.factory = factory;
    this.loaded = loaded;
    this.center = center;
  }

  @Override
  public boolean repositionCenter(SectionPos position) {
    boolean changed = !center.equals(position);
    center = position;
    return changed;
  }

  @Override
  public int radius() {
    return radius;
  }

  @Override
  public int minY() {
    return minY;
  }

  @Override
  public int maxY() {
    return maxY;
  }

  @Override
  public int height() {
    return maxY - minY + 1;
  }

  @Override
  public SectionPos centerSectionPos() {
    return center;
  }

  @Override
  public @Nullable T getValueAt(BlockPos position) {
    return getValue(SectionPos.asLong(position));
  }

  @Override
  public @Nullable T getValue(long node) {
    return getValue(SectionPos.x(node), SectionPos.y(node), SectionPos.z(node));
  }

  public @Nullable T getExisting(long node) {
    int y = SectionPos.y(node);
    if (y < minY || y > maxY) return null;
    var column = columns.get(ChunkPos.pack(SectionPos.x(node), SectionPos.z(node)));
    return column == null ? null : column.get(y - minY);
  }

  @Override
  public @Nullable T getValue(int x, int y, int z) {
    if (y < minY || y > maxY || !loaded.test(ChunkPos.pack(x, z))) return null;
    long position = ChunkPos.pack(x, z);
    var column = columns.get(position);
    var value = column == null ? null : column.get(y - minY);
    if (value != null) return value;
    synchronized (this) {
      if (!loaded.test(position)) return null;
      column = columns.get(position);
      if (column == null) {
        column = new AtomicReferenceArray<>(height());
        columns.put(position, column);
      }
      value = column.get(y - minY);
      if (value == null) {
        long node = SectionPos.asLong(x, y, z);
        value = factory.createValue(nextIndex++, node);
        if (value.getSectionNode() != node) value.setSectionNode(node);
        column.set(y - minY, value);
        count++;
        snapshot = null;
      }
      return value;
    }
  }

  public synchronized void removeColumn(int x, int z, Consumer<T> release) {
    var column = columns.remove(ChunkPos.pack(x, z));
    if (column == null) return;
    snapshot = null;
    for (int i = 0; i < column.length(); i++) {
      var value = column.get(i);
      if (value != null) {
        release.accept(value);
        count--;
      }
    }
  }

  private synchronized List<T> snapshot() {
    if (snapshot != null) return snapshot;
    var values = new ArrayList<T>(count);
    for (var column : columns.values())
      for (int i = 0; i < column.length(); i++) {
        var value = column.get(i);
        if (value != null) values.add(value);
      }
    snapshot = List.copyOf(values);
    return snapshot;
  }

  @Override
  public Iterator<T> iterator() {
    return snapshot().iterator();
  }

  @Override
  public void forEach(Consumer<? super T> action) {
    snapshot().forEach(action);
  }

  @Override
  public Spliterator<T> spliterator() {
    return snapshot().spliterator();
  }

  @Override
  public int size() {
    return count;
  }
}
