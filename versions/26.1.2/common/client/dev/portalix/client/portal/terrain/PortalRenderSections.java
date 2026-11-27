package dev.portalix.client.portal.terrain;

import dev.portalix.client.world.RetainedChunkStorage;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;
import java.util.function.LongPredicate;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/** World coordinates own section lifetime; moving a camera never repurposes a live section. */
public final class PortalRenderSections {
  @FunctionalInterface
  public interface Factory {
    RenderSection create(int index, long node);
  }

  private final int minY;
  private final int maxY;
  private final Factory factory;
  private final LongPredicate loaded;
  private volatile RetainedChunkStorage<AtomicReferenceArray<RenderSection>> columns = new RetainedChunkStorage<>();
  private int nextIndex;
  private volatile boolean closed;

  public PortalRenderSections(int minY, int maxY, Factory factory, LongPredicate loaded) {
    this.minY = minY;
    this.maxY = maxY;
    this.factory = factory;
    this.loaded = loaded;
  }

  public int minY() {
    return minY;
  }

  public int maxY() {
    return maxY;
  }

  public @Nullable RenderSection getValue(long node) {
    return getValue(SectionPos.x(node), SectionPos.y(node), SectionPos.z(node));
  }

  public @Nullable RenderSection getExisting(long node) {
    int y = SectionPos.y(node);
    if (y < minY || y > maxY) return null;
    var column = columns.get(ChunkPos.pack(SectionPos.x(node), SectionPos.z(node)));
    return column == null ? null : column.get(y - minY);
  }

  public @Nullable RenderSection getValue(int x, int y, int z) {
    if (closed || y < minY || y > maxY || !loaded.test(ChunkPos.pack(x, z))) return null;
    long position = ChunkPos.pack(x, z);
    var column = columns.get(position);
    var value = column == null ? null : column.get(y - minY);
    if (value != null) return value;
    synchronized (this) {
      if (closed || !loaded.test(position)) return null;
      column = columns.get(position);
      if (column == null) {
        column = new AtomicReferenceArray<>(maxY - minY + 1);
        columns.put(position, column);
      }
      value = column.get(y - minY);
      if (value == null) {
        long node = SectionPos.asLong(x, y, z);
        value = factory.create(nextIndex++, node);
        column.set(y - minY, value);
      }
      return value;
    }
  }

  public synchronized void removeColumn(int x, int z, Consumer<RenderSection> release) {
    var column = columns.remove(ChunkPos.pack(x, z));
    if (column == null) return;
    for (int i = 0; i < column.length(); i++) {
      var value = column.get(i);
      if (value != null) release.accept(value);
    }
  }

  public synchronized void clear(Consumer<RenderSection> release) {
    closed = true;
    for (var column : columns.values())
      for (int i = 0; i < column.length(); i++) {
        var value = column.get(i);
        if (value != null) release.accept(value);
      }
    columns = new RetainedChunkStorage<>();
  }
}
