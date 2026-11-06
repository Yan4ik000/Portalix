package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.compat.sodium.SodiumArenaBatch;
import dev.portalix.client.compat.sodium.SodiumDynamicSections;
import dev.portalix.client.compat.sodium.SodiumPortalIndices;
import dev.portalix.client.compat.sodium.SodiumRegionViews;
import dev.portalix.client.portal.scene.PortalSceneManager;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import net.caffeinemc.mods.sodium.client.gpu.device.batch.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(value = RenderRegion.class, remap = false)
abstract class SodiumRegionViewsMixin implements SodiumRegionViews, SodiumDynamicSections {
  @Shadow @Final private Map<TerrainRenderPass, MultiDrawBatch> cachedBatches;
  @Shadow @Final private Map<TerrainRenderPass, SectionRenderDataStorage> sectionRenderData;
  @Shadow private RenderRegion.DeviceResources resources;
  @Shadow private int sectionCount;
  @Unique private final Map<LevelRenderer, View> portalix$views = new IdentityHashMap<>();
  @Unique private final long[] portalix$dynamic = new long[RenderRegion.REGION_SIZE / Long.SIZE];

  @Override
  public void portalix$removeGeometry(long columns) {
    var region = (RenderRegion) (Object) this;
    var sections = ((RenderRegionAccessor) region).portalix$sections();
    int count = 0;
    for (long remaining = columns; remaining != 0; remaining &= remaining - 1) {
      int first = Long.numberOfTrailingZeros(remaining) << RenderRegion.REGION_HEIGHT_SH;
      for (int y = 0; y < RenderRegion.REGION_HEIGHT; y++) if (sections[first + y] != null) count++;
    }
    if (count == 0) return;
    boolean complete = count == sectionCount;
    var geometry = resources == null ? null : resources.getGeometryAllocator();
    var indices = resources == null ? null : resources.getIndexAllocator();
    var geometryArena =
        geometry == null ? null : geometry.getBackingArena();
    var indexArena =
        indices == null ? null : indices.getBackingArena();
    var geometryBatch = geometryArena instanceof SodiumArenaBatch batch ? batch : null;
    var indexBatch = indexArena instanceof SodiumArenaBatch batch ? batch : null;
    if (geometryBatch != null) geometryBatch.portalix$beginRelease(geometry);
    try {
      if (indexBatch != null) indexBatch.portalix$beginRelease(indices);
      try {
        if (complete) {
          SodiumPortalIndices.remove(region);
          for (var storage : sectionRenderData.values()) storage.delete();
        } else {
          for (long remaining = columns; remaining != 0; remaining &= remaining - 1) {
            int first = Long.numberOfTrailingZeros(remaining) << RenderRegion.REGION_HEIGHT_SH;
            for (int y = 0; y < RenderRegion.REGION_HEIGHT; y++) {
              var section = sections[first + y];
              if (section == null) continue;
              SodiumPortalIndices.remove(section);
              for (var storage : sectionRenderData.values())
                storage.removeData(section.getSectionIndex());
            }
          }
        }
      } finally {
        if (indexBatch != null) indexBatch.portalix$endRelease();
      }
    } finally {
      if (geometryBatch != null) geometryBatch.portalix$endRelease();
    }
    if (complete) {
      sectionRenderData.clear();
      if (resources != null) {
        resources.delete();
        resources = null;
      }
    }
    region.clearAllCachedBatches();
  }

  @Override
  public long[] portalix$dynamicSections() {
    return portalix$dynamic;
  }

  @Override
  public void portalix$dynamicSection(int sectionIndex, boolean dynamic) {
    int word = sectionIndex >>> 6;
    long bit = 1L << (sectionIndex & 63);
    if (dynamic) portalix$dynamic[word] |= bit;
    else portalix$dynamic[word] &= ~bit;
  }

  @Unique
  private static boolean portalix$isPortalView() {
    return PortalSceneManager.isPortalView() && !IrisShaders.shadowPass();
  }

  @Unique
  private View portalix$view() {
    return portalix$views.computeIfAbsent(
        Minecraft.getInstance().levelRenderer, ignored -> new View((RenderRegion) (Object) this));
  }

  @ModifyReturnValue(method = "getRenderList", at = @At("RETURN"))
  private ChunkRenderList portalix$list(ChunkRenderList original) {
    return portalix$isPortalView() ? portalix$view().list : original;
  }

  @Inject(method = "getCachedBatch", at = @At("HEAD"), cancellable = true)
  private void portalix$batch(TerrainRenderPass pass, CallbackInfoReturnable<MultiDrawBatch> cir) {
    if (portalix$isPortalView())
      cir.setReturnValue(
          portalix$view()
              .batches
              .computeIfAbsent(
                  pass,
                  ignored ->
                      MultiDrawBatch.newBatch(
                          ModelQuadFacing.COUNT * RenderRegion.REGION_SIZE + 1)));
  }

  @Inject(method = "clearAllCachedBatches", at = @At("HEAD"))
  private void portalix$geometryChanged(CallbackInfo ci) {
    for (var view : portalix$views.values()) view.batches.values().forEach(MultiDrawBatch::clear);
  }

  @Inject(method = "clearCachedBatchFor", at = @At("HEAD"))
  private void portalix$passChanged(TerrainRenderPass pass, CallbackInfo ci) {
    for (var view : portalix$views.values()) {
      var batch = view.batches.get(pass);
      if (batch != null) batch.clear();
    }
  }

  @Override
  public void portalix$invalidateView() {
    var batches = portalix$isPortalView() ? portalix$view().batches : cachedBatches;
    batches.values().forEach(MultiDrawBatch::clear);
  }

  @Override
  public void portalix$invalidateView(TerrainRenderPass pass) {
    var batches = portalix$isPortalView() ? portalix$view().batches : cachedBatches;
    var batch = batches.get(pass);
    if (batch != null) batch.clear();
  }

  @Override
  public void portalix$releaseView(LevelRenderer renderer) {
    var view = portalix$views.remove(renderer);
    if (view != null) view.batches.values().forEach(MultiDrawBatch::delete);
  }

  @Inject(method = "delete", at = @At("HEAD"))
  private void portalix$delete(CallbackInfo ci) {
    Arrays.fill(portalix$dynamic, 0);
    for (var view : portalix$views.values()) view.batches.values().forEach(MultiDrawBatch::delete);
    portalix$views.clear();
  }

  @Unique
  private static final class View {
    final ChunkRenderList list;
    final Map<TerrainRenderPass, MultiDrawBatch> batches = new IdentityHashMap<>();

    View(RenderRegion region) {
      list = new ChunkRenderList(region);
    }
  }
}
