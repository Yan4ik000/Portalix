package dev.portalix.client.compat.sodium;

import it.unimi.dsi.fastutil.longs.LongCollection;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;

public interface SharedSodiumTerrain {
  void portalix$unloadChunks(LongCollection chunks);

  void portalix$portalView(Camera camera, Viewport viewport, FogParameters fog, boolean culling);

  void portalix$restorePrimary();

  void portalix$queueSort(SodiumViewSortTask task);

  void portalix$queueShadowView();

  void portalix$exchangePrimary(LevelRenderer renderer);
}
