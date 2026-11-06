package dev.portalix.client.mixin.scene;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.compat.minecraft.VanillaSceneTerrain;
import dev.portalix.client.portal.scene.PortalSceneManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelExtractor.class)
abstract class TerrainOnlyBlockEntitiesMixin {
  @WrapOperation(
      method = "extractVisibleBlockEntities(Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/culling/Frustum;)V",
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/client/renderer/LevelRenderer;visibleSections()Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"),
      require = 1, allow = 1)
  private ObjectArrayList<SectionRenderDispatcher.RenderSection> portalix$blockEntityWindow(
      LevelRenderer renderer,
      Operation<ObjectArrayList<SectionRenderDispatcher.RenderSection>> original) {
    return VanillaSceneTerrain.renderSections(original.call(renderer));
  }

  @WrapWithCondition(method = "extract", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/renderer/extract/LevelExtractor;extractVisibleBlockEntities(Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/culling/Frustum;)V"), require = 1, allow = 1)
  private boolean portalix$blockEntities(LevelExtractor extractor, Camera camera,
      float partial, LevelRenderState state, Frustum frustum) {
    return !PortalSceneManager.terrainOnly();
  }
}
