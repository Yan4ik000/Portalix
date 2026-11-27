package dev.portalix.client.portal.scene;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

public final class SceneTerrainChanges {
  public final LongOpenHashSet addedLoadedChunks = new LongOpenHashSet();
  public final LongOpenHashSet removedLoadedChunks = new LongOpenHashSet();
  public final LongOpenHashSet addedEmptySections = new LongOpenHashSet();
  public final LongOpenHashSet removedEmptySections = new LongOpenHashSet();

  public void clear() {
    addedLoadedChunks.clear();
    removedLoadedChunks.clear();
    addedEmptySections.clear();
    removedEmptySections.clear();
  }
}
