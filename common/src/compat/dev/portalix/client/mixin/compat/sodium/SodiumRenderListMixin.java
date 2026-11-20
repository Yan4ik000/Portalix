package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.compat.sodium.SodiumRegionViews;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(value = ChunkRenderList.class, remap = false)
abstract class SodiumRenderListMixin {
  @WrapOperation(
      method = "prepareForRender",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegion;clearAllCachedBatches()V"))
  private void portalix$viewChanged(RenderRegion region, Operation<Void> original) {
    ((SodiumRegionViews) region).portalix$invalidateView();
  }
}
