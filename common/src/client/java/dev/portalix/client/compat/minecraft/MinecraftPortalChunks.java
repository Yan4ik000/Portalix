package dev.portalix.client.compat.minecraft;

import dev.portalix.client.compat.PortalTerrainBackend;
import dev.portalix.client.portal.PortalViewReadiness;
import dev.portalix.client.portal.terrain.PortalTerrainColumns;
import dev.portalix.client.world.RetainedChunkStorage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

public final class MinecraftPortalChunks {
  public interface Access {
    MinecraftPortalChunks portalix$chunks();
  }

  private final RetainedChunkStorage<LevelChunk> overflow = new RetainedChunkStorage<>();
  private final @Nullable PortalTerrainColumns columns;
  private @Nullable List<LevelChunk> loaded;

  public MinecraftPortalChunks(ClientLevel level) {
    columns = PortalTerrainBackend.usesVanillaChunkEvents()
        ? new PortalTerrainColumns(level.getMinSectionY(), level.getMaxSectionY() - level.getMinSectionY() + 1)
        : null;
  }

  public @Nullable PortalTerrainColumns columns() {
    return columns;
  }

  public @Nullable LevelChunk find(@Nullable LevelChunk slot, int x, int z) {
    if (slot != null && slot.getPos().x() == x && slot.getPos().z() == z) return slot;
    return overflow.get(ChunkPos.pack(x, z));
  }

  public List<LevelChunk> loaded(AtomicReferenceArray<LevelChunk> slots) {
    if (loaded == null) {
      var chunks = new ArrayList<>(overflow.values());
      for (int i = 0; i < slots.length(); i++) {
        var chunk = slots.get(i);
        if (chunk != null) chunks.add(chunk);
      }
      loaded = List.copyOf(chunks);
    }
    return loaded;
  }

  public void retain(LevelChunk chunk) {
    overflow.put(chunk.getPos().pack(), chunk);
  }

  public boolean forget(LevelChunk chunk) {
    long key = chunk.getPos().pack();
    return overflow.get(key) == chunk && overflow.remove(key) != null;
  }

  public void updateColumn(LevelChunk chunk) {
    var pos = chunk.getPos();
    if (columns != null) columns.replace(pos.x(), pos.z(), chunk.getSections());
  }

  public void added() {
    loaded = null;
    PortalViewReadiness.chunkLoaded();
  }

  public void removed(ChunkPos pos) {
    loaded = null;
    if (columns != null) columns.remove(pos.x(), pos.z());
    PortalViewReadiness.chunkForgotten(pos);
  }

  public void sectionChanged(int x, int y, int z, boolean empty) {
    if (columns != null) columns.setEmpty(x, y, z, empty);
  }
}
