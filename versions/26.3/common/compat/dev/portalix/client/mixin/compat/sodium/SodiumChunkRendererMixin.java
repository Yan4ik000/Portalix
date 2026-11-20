package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.compat.sodium.SodiumPortalIndices;
import net.caffeinemc.mods.sodium.client.gpu.device.batch.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataUnsafe;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = DefaultChunkRenderer.class, remap = false)
abstract class SodiumChunkRendererMixin {
  // Iris prepares its shadow batches after preparing the color batches for the same view.
  @Unique private final boolean[] portalix$shadowLayers = new boolean[DefaultTerrainRenderPasses.ALL.length];

  @ModifyExpressionValue(
      method = {"prepare", "render"},
      at = @At(value = "FIELD", target =
          "Lnet/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer;shouldDraw:[Z"),
      require = 2, allow = 2)
  private boolean[] portalix$preparedLayers(boolean[] layers) {
    return IrisShaders.shadowPass() ? portalix$shadowLayers : layers;
  }

  @Inject(method = "prepare", at = @At("HEAD"))
  private void portalix$prepare(
      ChunkRenderListIterable lists, CameraTransform camera, boolean indexed, CallbackInfo ci) {
    if (indexed && !IrisShaders.shadowPass())
      SodiumPortalIndices.prepare(lists, camera);
  }

  @WrapOperation(
      method = "fillCommandBuffer",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/caffeinemc/mods/sodium/client/render/chunk/data/SectionRenderDataStorage;getDataPointer(I)J"))
  private static long portalix$sectionIndex(
      SectionRenderDataStorage storage,
      int index,
      Operation<Long> original,
      @Share("sectionIndex") LocalIntRef sectionIndex) {
    sectionIndex.set(index);
    return original.call(storage, index);
  }

  @WrapOperation(
      method = "fillCommandBuffer",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer;addLocalIndexedDrawCommands(Lnet/caffeinemc/mods/sodium/client/gpu/device/batch/MultiDrawBatch;JI)V"))
  private static void portalix$indices(
      MultiDrawBatch batch,
      long data,
      int mask,
      Operation<Void> original,
      @Local(argsOnly = true) RenderRegion region,
      @Share("sectionIndex") LocalIntRef sectionIndex) {
    long offset =
        IrisShaders.shadowPass() ? -1 : SodiumPortalIndices.offset(region, sectionIndex.get());
    if (offset < 0) {
      original.call(batch, data, mask);
      return;
    }
    long previous = SectionRenderDataUnsafe.getBaseElement(data);
    SectionRenderDataUnsafe.setLocalBaseElement(data, offset);
    try {
      original.call(batch, data, mask);
    } finally {
      SectionRenderDataUnsafe.setLocalBaseElement(data, previous);
    }
  }
}
