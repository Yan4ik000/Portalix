package dev.portalix.client.world;

import dev.portalix.client.portal.terrain.PortalTerrainColumns;
import java.util.List;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

public interface RetainedChunkAccess {
  List<LevelChunk> portalix$retainedChunks();

  @Nullable PortalTerrainColumns portalix$terrainColumns();
}
