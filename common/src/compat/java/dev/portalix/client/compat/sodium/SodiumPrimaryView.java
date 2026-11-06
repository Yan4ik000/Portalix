package dev.portalix.client.compat.sodium;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;

public interface SodiumPrimaryView {
  void portalix$restorePrimary();

  void portalix$exchangePrimary(LevelRenderer renderer);

  boolean portalix$completeDemand(LevelRenderer renderer, CameraRenderState camera);

  boolean portalix$primaryOccludes(CameraRenderState camera, AABB bounds);

  void portalix$releaseView(LevelRenderer renderer);

  void portalix$retargetView(LevelRenderer renderer);

  void portalix$invalidateOcclusion();

  void portalix$queueSort(SodiumViewSortTask task);
}
