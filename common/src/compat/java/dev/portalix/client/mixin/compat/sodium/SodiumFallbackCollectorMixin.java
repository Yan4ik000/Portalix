package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.LocalSectionIndex;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.FallbackVisibleChunkCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.VisibleChunkCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(value = FallbackVisibleChunkCollector.class, remap = false)
abstract class SodiumFallbackCollectorMixin {
  @Shadow @Final private VisibleChunkCollector renderListCollector;

  @WrapOperation(
      method = "visit(III)V",
      at = @At(value = "INVOKE", target =
          "Lnet/caffeinemc/mods/sodium/client/render/chunk/storage/SectionStorage;getCurrent(III)Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;"))
  private RenderSection portalix$regionSection(
      SectionStorage sections, int x, int y, int z, Operation<RenderSection> original) {
    var access = (VisibleChunkCollectorAccessor) renderListCollector;
    var region = access.portalix$regions().getForChunk(x, y, z);
    if (region == null) return null;
    int index =
        LocalSectionIndex.pack(
            x & (RenderRegion.REGION_WIDTH - 1),
            y & (RenderRegion.REGION_HEIGHT - 1),
            z & (RenderRegion.REGION_LENGTH - 1));
    // Region membership is current while Sodium defers publishing its culling lookup table.
    return ((RenderRegionAccessor) region).portalix$sections()[index];
  }
}
