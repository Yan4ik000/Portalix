package dev.portalix.neoforge.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = LevelExtractor.class, priority = 1100)
abstract class SodiumBlockEntityExtractionMixin {
  @Shadow protected abstract void extractVisibleBlockEntities(
      Camera camera, float partialTick, LevelRenderState state);

  // The terrain-only condition in TerrainOnlyExtractionMixin also guards this call.
  @WrapOperation(method = "extract", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/renderer/extract/LevelExtractor;extractVisibleBlockEntities(Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/state/level/LevelRenderState;)V"),
      order = 900, allow = 1)
  private void portalix$extractBlockEntities(LevelExtractor extractor, Camera camera, float partialTick,
      LevelRenderState state, Operation<Void> original) {
    extractVisibleBlockEntities(camera, partialTick, state, camera.getCullFrustum());
  }

  // Sodium 0.9.2 targets the overload NeoForge used before the camera supplied the frustum directly.
  private void extractVisibleBlockEntities(
      Camera camera, float partialTick, LevelRenderState state, Frustum frustum) {
    extractVisibleBlockEntities(camera, partialTick, state);
  }
}
