package dev.portalix.client.compat.minecraft;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.renderer.extract.LevelExtractor;

public interface SceneExtractorAccess {
  void portalix$shareTerrain(LevelExtractor primary);

  void portalix$invalidateView();

  boolean portalix$consumeViewInvalidation();

  boolean portalix$dirtyTerrain(long section);

  void portalix$unloadColumns(LongSet columns);
}
