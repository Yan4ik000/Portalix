package dev.portalix.client.world;

import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.world.level.chunk.LevelChunk;

public interface PortalChunkStorageAccess {
  boolean portalix$inRange(int x, int z);

  int portalix$index(int x, int z);

  AtomicReferenceArray<LevelChunk> portalix$slots();

  void portalix$inheritTracking(PortalChunkStorageAccess previous);
}
