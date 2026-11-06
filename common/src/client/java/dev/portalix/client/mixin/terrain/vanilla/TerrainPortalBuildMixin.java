package dev.portalix.client.mixin.terrain.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.portal.scene.PortalSceneManager;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class TerrainPortalBuildMixin {
  @Inject(
      method = "render",
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;uploadTerrainBuffersToGpu()V",
          shift = At.Shift.AFTER))
  private void portalix$publishedMeshes(CallbackInfo ci) {
    if (!PortalSceneManager.isPortalView()) PortalSceneManager.publishTerrainChanges();
  }

  @WrapOperation(
      method = "render",
      at = {
        @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;lock()V"),
        @At(
            value = "INVOKE",
            target =
                "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;uploadTerrainBuffersToGpu()V"),
        @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;unlock()V")
      })
  private void portalix$publishOnce(SectionRenderDispatcher dispatcher, Operation<Void> original) {
    if (!PortalSceneManager.isPortalView()) original.call(dispatcher);
  }
}
