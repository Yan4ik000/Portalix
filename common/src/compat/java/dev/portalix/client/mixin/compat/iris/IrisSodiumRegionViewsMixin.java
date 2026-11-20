package dev.portalix.client.mixin.compat.iris;

import dev.portalix.client.compat.sodium.SodiumRegionViews;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderRegion.class, priority = 900, remap = false)
abstract class IrisSodiumRegionViewsMixin {
  @Inject(method = "iris$forceClearAllBatches", at = @At("HEAD"), allow = 1)
  private void portalix$geometryChanged(CallbackInfo ci) {
    ((SodiumRegionViews) this).portalix$invalidatePortalBatches();
  }

  @Inject(method = "iris$forceClearBatchFor", at = @At("HEAD"), allow = 1)
  private void portalix$passChanged(TerrainRenderPass pass, CallbackInfo ci) {
    ((SodiumRegionViews) this).portalix$invalidatePortalBatches(pass);
  }
}
