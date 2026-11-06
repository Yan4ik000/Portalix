package dev.portalix.client.portal.terrain;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class PortalTerrainColumns {
  private final int minY;
  private final int height;
  private final Long2ObjectOpenHashMap<long[]> columns = new Long2ObjectOpenHashMap<>();

  public PortalTerrainColumns(int minY, int height) {
    this.minY = minY;
    this.height = height;
  }

  public int minY() {
    return minY;
  }

  public long[] get(int x, int z) {
    return columns.get(ChunkPos.pack(x, z));
  }

  public void replace(int x, int z, LevelChunkSection[] sections) {
    var occupied = new long[(height + Long.SIZE - 1) / Long.SIZE];
    for (int i = 0; i < sections.length; i++)
      if (!sections[i].hasOnlyAir()) occupied[i / Long.SIZE] |= 1L << (i % Long.SIZE);
    columns.put(ChunkPos.pack(x, z), occupied);
  }

  public void remove(int x, int z) {
    columns.remove(ChunkPos.pack(x, z));
  }

  public void setEmpty(int x, int y, int z, boolean empty) {
    int index = y - minY;
    var occupied = get(x, z);
    if (occupied == null || index < 0 || index >= height) return;
    int word = index / Long.SIZE;
    long bit = 1L << (index % Long.SIZE);
    if (empty) occupied[word] &= ~bit;
    else occupied[word] |= bit;
  }
}
