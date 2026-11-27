package dev.portalix.client.compat;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;

public interface TerrainIntegration {
  TerrainIntegration INSTANCE = OptionalRenderServices.load(TerrainIntegration.class, "sodium");

  void attach(LevelRenderer renderer, LevelRenderer owner);
  void release(LevelRenderer renderer);
  void restorePrimary();
  void exchangePrimary(LevelRenderer renderer);
  boolean meshesReady(LevelRenderer renderer, CameraRenderState camera);
  static boolean usesOcclusionCulling(CameraRenderState camera) {
    return camera.smartCull;
  }

  boolean primaryOccludes(CameraRenderState camera, AABB bounds);
  void retarget(LevelRenderer renderer);
  void invalidateOcclusion(LevelRenderer renderer);
}
