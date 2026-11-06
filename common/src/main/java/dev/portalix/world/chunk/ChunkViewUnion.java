package dev.portalix.world.chunk;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;

public final class ChunkViewUnion {
  private ChunkViewUnion() {}

  public static LongOpenHashSet collect(LongCollection centers, int radius) {
    if (centers.isEmpty()) return new LongOpenHashSet();
    var rows = new Int2ObjectOpenHashMap<LongArrayList>();
    int[] widths = new int[radius + 2];
    int width = radius + 1;
    for (int z = 0; z < widths.length; z++) {
      while (width >= 0 && !ChunkTrackingView.isWithinDistance(0, 0, radius, width, z, true))
        width--;
      widths[z] = width;
    }
    for (long center : centers) {
      int x = ChunkPos.getX(center), z = ChunkPos.getZ(center);
      for (int dz = -radius - 1; dz <= radius + 1; dz++) {
        int extent = widths[Math.abs(dz)];
        if (extent < 0) continue;
        rows.computeIfAbsent(z + dz, ignored -> new LongArrayList())
            .add(ChunkPos.pack(x - extent, x + extent));
      }
    }
    int count = 0;
    for (var row : rows.int2ObjectEntrySet()) {
      var intervals = row.getValue();
      intervals.sort((a, b) -> Integer.compare(ChunkPos.getX(a), ChunkPos.getX(b)));
      int start = ChunkPos.getX(intervals.getLong(0));
      int end = ChunkPos.getZ(intervals.getLong(0));
      int merged = 0;
      for (int i = 1; i < intervals.size(); i++) {
        long next = intervals.getLong(i);
        int left = ChunkPos.getX(next), right = ChunkPos.getZ(next);
        if (left <= end + 1) end = Math.max(end, right);
        else {
          intervals.set(merged++, ChunkPos.pack(start, end));
          count += end - start + 1;
          start = left;
          end = right;
        }
      }
      intervals.set(merged++, ChunkPos.pack(start, end));
      count += end - start + 1;
      intervals.size(merged);
    }
    var chunks = new LongOpenHashSet(count);
    for (var row : rows.int2ObjectEntrySet())
      for (long interval : row.getValue())
        addRow(chunks, row.getIntKey(), ChunkPos.getX(interval), ChunkPos.getZ(interval));
    return chunks;
  }

  private static void addRow(LongOpenHashSet chunks, int z, int start, int end) {
    for (int x = start; x <= end; x++) chunks.add(ChunkPos.pack(x, z));
  }
}
