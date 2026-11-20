package dev.portalix.client.mixin.terrain.vanilla;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.portal.scene.PortalSceneManager;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
abstract class TerrainPortalBuildMixin {
  @Definition(id = "dispatcher", field =
      "Lnet/minecraft/client/renderer/LevelRenderer;sectionRenderDispatcher:Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;")
  @Expression("this.dispatcher != null")
  @ModifyExpressionValue(method = "render", at = @At("MIXINEXTRAS:EXPRESSION"), allow = 1)
  private boolean portalix$uploadPrimaryTerrain(boolean hasDispatcher) {
    return hasDispatcher && !PortalSceneManager.isPortalView();
  }

  @WrapOperation(
      method = "render",
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;uploadTerrainBuffersToGpu()V"),
      allow = 1)
  private void portalix$publishMeshes(SectionRenderDispatcher dispatcher, Operation<Void> original) {
    original.call(dispatcher);
    PortalSceneManager.publishTerrainChanges();
  }
}
