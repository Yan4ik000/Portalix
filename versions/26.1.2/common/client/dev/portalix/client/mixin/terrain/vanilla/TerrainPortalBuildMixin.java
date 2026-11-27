package dev.portalix.client.mixin.terrain.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.portal.scene.PortalSceneManager;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
abstract class TerrainPortalBuildMixin {
  @WrapOperation(method = "prepareChunkRenders", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;uploadGlobalGeomBuffersToGPU()V"), allow = 1)
  private void portalix$publishMeshes(SectionRenderDispatcher dispatcher, Operation<Void> original) {
    if (PortalSceneManager.isPortalView()) return;
    original.call(dispatcher);
    PortalSceneManager.publishTerrainChanges();
  }
}
