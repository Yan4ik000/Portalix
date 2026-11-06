package dev.portalix.client.compat.minecraft;

import dev.portalix.client.compat.PortalTerrainBackend;
import dev.portalix.client.platform.ClientPlatform;
import dev.portalix.client.portal.terrain.PortalTerrainColumns;
import dev.portalix.client.world.RetainedChunkStorage;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

public final class MinecraftPortalChunks {
  private final ClientChunkCache cache;
  private final ClientLevel level;
  private final LevelChunk emptyChunk;
  private final RetainedChunkStorage<LevelChunk> chunks = new RetainedChunkStorage<>();
  private final PortalTerrainColumns columns;

  public MinecraftPortalChunks(ClientChunkCache cache, ClientLevel level, LevelChunk emptyChunk) {
    this.cache = cache;
    this.level = level;
    this.emptyChunk = emptyChunk;
    columns = PortalTerrainBackend.usesVanillaChunkEvents()
        ? new PortalTerrainColumns(level.getMinSectionY(), level.getMaxSectionY() - level.getMinSectionY() + 1)
        : null;
  }

  public PortalTerrainColumns columns() {
    return columns;
  }

  public List<LevelChunk> retained() {
    return chunks.values();
  }

  public int size() {
    return chunks.size();
  }

  public LevelChunk get(int x, int z, boolean load) {
    var chunk = chunks.get(ChunkPos.pack(x, z));
    return chunk == null && load ? emptyChunk : chunk;
  }

  public LevelChunk receive(int x, int z, FriendlyByteBuf buffer,
      Map<Heightmap.Types, long[]> heightmaps,
      Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> blockEntities) {
    var pos = new ChunkPos(x, z);
    var chunk = chunks.get(pos.pack());
    boolean added = chunk == null;
    if (added) chunk = new LevelChunk(level, pos);
    chunk.replaceWithPacketData(buffer, heightmaps, blockEntities);
    chunks.put(pos.pack(), chunk);
    cache.removedLoadedChunks().remove(pos.pack());
    if (added) cache.addedLoadedChunks().add(pos.pack());
    sections(chunk, false);
    level.onChunkLoaded(pos);
    PortalTerrainBackend.chunkLoaded(level, x, z);
    ClientPlatform.INSTANCE.chunkLoaded(level, chunk);
    return chunk;
  }

  public void replaceBiomes(int x, int z, FriendlyByteBuf buffer) {
    var chunk = chunks.get(ChunkPos.pack(x, z));
    if (chunk != null) chunk.replaceBiomes(buffer);
  }

  public void drop(ChunkPos pos) {
    var chunk = chunks.get(pos.pack());
    if (chunk != null) {
      ClientPlatform.INSTANCE.chunkUnloaded(level, chunk);
      chunks.remove(pos.pack());
      cache.addedLoadedChunks().remove(pos.pack());
      cache.removedLoadedChunks().add(pos.pack());
      sections(chunk, true);
      level.unload(chunk);
      PortalTerrainBackend.chunkUnloaded(level, pos.x(), pos.z());
    }
  }

  public void sectionChanged(int x, int y, int z, boolean empty) {
    if (chunks.get(ChunkPos.pack(x, z)) != null) {
      if (columns != null) columns.setEmpty(x, y, z, empty);
      long section = SectionPos.asLong(x, y, z);
      (empty ? cache.removedEmptySections() : cache.addedEmptySections()).remove(section);
      (empty ? cache.addedEmptySections() : cache.removedEmptySections()).add(section);
    }
  }

  private void sections(LevelChunk chunk, boolean removed) {
    var pos = chunk.getPos();
    var sections = chunk.getSections();
    if (columns != null) {
      if (removed) columns.remove(pos.x(), pos.z());
      else columns.replace(pos.x(), pos.z(), sections);
    }
    for (int i = 0; i < sections.length; i++) {
      long section = SectionPos.asLong(pos.x(), chunk.getSectionYFromSectionIndex(i), pos.z());
      boolean empty = !removed && sections[i].hasOnlyAir();
      (empty ? cache.removedEmptySections() : cache.addedEmptySections()).remove(section);
      (empty ? cache.addedEmptySections() : cache.removedEmptySections()).add(section);
    }
  }
}
