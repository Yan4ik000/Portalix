package dev.portalix.client.mixin.terrain;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.portal.scene.PortalSceneManager;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
abstract class SharedTerrainViewMixin {
  @WrapOperation(method = "cullTerrain", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/renderer/ViewArea;repositionCamera(Lnet/minecraft/core/SectionPos;)V"))
  private void portalix$viewCenter(ViewArea area, SectionPos position, Operation<Void> original) {
    if (!PortalSceneManager.limitedTerrain() && !PortalSceneManager.isPortalView())
      original.call(area, position);
  }
}
