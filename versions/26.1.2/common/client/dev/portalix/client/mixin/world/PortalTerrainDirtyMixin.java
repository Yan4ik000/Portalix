package dev.portalix.client.mixin.world;

import dev.portalix.client.portal.scene.PortalSceneManager;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class PortalTerrainDirtyMixin {
  @Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"))
  private void portalix$invalidateLightAndBlocks(int x, int y, int z, boolean playerChanged, CallbackInfo ci) {
    PortalSceneManager.invalidateTerrainPreparation(x, y, z);
  }
}
