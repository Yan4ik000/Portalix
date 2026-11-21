package dev.portalix.world.chunk;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import net.minecraft.world.level.ChunkPos;

public final class PortalLoadingSources {
  private PortalLoadingSources() {}

  public static void addRegion(Long2IntMap sources, long center, int radius, int nativeRadius) {
    int tileRadius = Math.min(radius, nativeRadius);
    int offset = radius - tileRadius;
    int stride = tileRadius * 2 + 1;
    int weight = nativeRadius - tileRadius;
    int centerX = ChunkPos.getX(center), centerZ = ChunkPos.getZ(center);
    // The native tracker propagates in squares. Sizes beyond its range are covered by smaller sources.
    for (int x = -offset; ; x = Math.min(x + stride, offset)) {
      for (int z = -offset; ; z = Math.min(z + stride, offset)) {
        long source = ChunkPos.pack(centerX + x, centerZ + z);
        if (weight < sources.getOrDefault(source, Integer.MAX_VALUE)) sources.put(source, weight);
        if (z == offset) break;
      }
      if (x == offset) break;
    }
  }
}
