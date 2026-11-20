package dev.portalix.client.mixin.terrain;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.client.compat.PortalTerrainBackend;
import dev.portalix.client.portal.scene.PortalSceneManager;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ChunkLoadingRenderState;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
abstract class SharedTerrainViewMixin {
  @Unique private SectionPos portalix$lastRenderCameraSection;

  @WrapOperation(
      method = "repositionCamera",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/ViewArea;repositionCamera(Lnet/minecraft/core/SectionPos;)Z"))
  private boolean portalix$viewCenter(ViewArea area, SectionPos pos, Operation<Boolean> original,
      @Local(argsOnly = true) CameraRenderState camera) {
    boolean vanilla = PortalTerrainBackend.usesVanillaChunkEvents();
    if (vanilla || !PortalSceneManager.isPortalView())
      original.call(area, vanilla && PortalSceneManager.limitedTerrain()
          ? SectionPos.of(PortalSceneManager.terrainOrigin(camera.pos)) : pos);
    // Vanilla uses the return value to rebuild the border mesh around the actual camera.
    boolean cameraMoved = !pos.equals(portalix$lastRenderCameraSection);
    portalix$lastRenderCameraSection = pos;
    return cameraMoved;
  }

  @WrapOperation(
      method = "render",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/SectionOcclusionGraph;update(Lnet/minecraft/client/renderer/state/level/CameraRenderState;ILnet/minecraft/client/renderer/state/level/ChunkLoadingRenderState;)V"))
  private void portalix$viewGraph(
      SectionOcclusionGraph graph,
      CameraRenderState camera,
      int fov,
      ChunkLoadingRenderState chunks,
      Operation<Void> original) {
    if (PortalTerrainBackend.usesVanillaChunkEvents()) original.call(graph, camera, fov, chunks);
  }
}
