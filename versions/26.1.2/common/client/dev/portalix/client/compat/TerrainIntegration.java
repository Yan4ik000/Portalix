package dev.portalix.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

public interface TerrainIntegration {
  TerrainIntegration INSTANCE = OptionalRenderServices.load(TerrainIntegration.class, "sodium");

  void attach(LevelRenderer renderer, LevelRenderer owner);
  void release(LevelRenderer renderer);
  void restorePrimary();
  void exchangePrimary(LevelRenderer renderer);
  boolean meshesReady(LevelRenderer renderer, CameraRenderState camera);
  boolean primaryOccludes(CameraRenderState camera, AABB bounds);
  void retarget(LevelRenderer renderer);
  void invalidateOcclusion(LevelRenderer renderer);

  static boolean usesOcclusionCulling(CameraRenderState camera) {
    var client = Minecraft.getInstance();
    return usesOcclusionCulling(client.level, camera.blockPos, client.player.isSpectator());
  }

  static boolean usesOcclusionCulling(ClientLevel level, BlockPos camera, boolean spectator) {
    return Minecraft.getInstance().smartCull && !(spectator && level.getBlockState(camera).isSolidRender());
  }
}
