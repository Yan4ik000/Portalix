package dev.portalix.client.compat.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.minecraft.client.renderer.LevelRenderer;

public interface SodiumRegionViews {
  void portalix$removeGeometry(long columns);

  void portalix$invalidateView();

  void portalix$invalidateView(TerrainRenderPass pass);

  void portalix$releaseView(LevelRenderer renderer);
}
