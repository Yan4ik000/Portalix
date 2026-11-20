package dev.portalix.client.mixin.terrain.vanilla;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.portal.terrain.PortalFrameTerrainBudget;
import dev.portalix.client.portal.terrain.PortalRenderSections;
import dev.portalix.client.portal.terrain.SectionUpdateStorageAccess;
import dev.portalix.client.portal.scene.PortalSceneManager;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.SectionUpdateTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
abstract class TerrainExtractionBudgetMixin {
  @Shadow @Final private LevelRenderer levelRenderer;
  @Unique private PortalFrameTerrainBudget.Allocation portalix$budget;

  @Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"))
  private void portalix$terrainDemandChanged(
      int x, int y, int z, boolean important, CallbackInfo ci) {
    PortalSceneManager.invalidateTerrainPreparation(x, y, z);
  }

  @WrapMethod(method = "extract")
  private void portalix$extractionBudget(
      DeltaTracker delta, Camera camera, float partial, Operation<Void> original) {
    var previous = portalix$budget;
    var budget = PortalFrameTerrainBudget.claim(levelRenderer);
    portalix$budget = budget;
    try {
      original.call(delta, camera, partial);
    } finally {
      portalix$budget = previous;
      budget.finish();
    }
  }

  @WrapOperation(
      method = "extract",
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/client/SectionUpdateTracker;getDirtyState(J)Lnet/minecraft/client/SectionUpdateTracker$SectionDirtyState;"))
  private SectionUpdateTracker.SectionDirtyState portalix$existingDirtyState(
      SectionUpdateTracker tracker, long node,
      Operation<SectionUpdateTracker.SectionDirtyState> original) {
    var storage = (PortalRenderSections<SectionUpdateTracker.SectionDirtyState>)
        ((SectionUpdateStorageAccess) tracker).portalix$storage();
    var state = storage.getExisting(node);
    return state == null ? original.call(tracker, node) : state;
  }

  @WrapOperation(
      method = "extract",
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/client/SectionUpdateTracker$SectionDirtyState;isDirty()Z"))
  private boolean portalix$deferSnapshot(
      SectionUpdateTracker.SectionDirtyState state, Operation<Boolean> original) {
    return original.call(state)
        && (state.isDirtyFromPlayer() || portalix$budget.canPrepare());
  }

  @WrapOperation(
      method = "extract",
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/client/renderer/chunk/RenderRegionCache;createRegion(Lnet/minecraft/client/multiplayer/ClientLevel;J)Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;"))
  private RenderSectionRegion portalix$measureSnapshot(
      RenderRegionCache cache, ClientLevel level, long section,
      Operation<RenderSectionRegion> original) {
    long start = System.nanoTime();
    try {
      return original.call(cache, level, section);
    } finally {
      portalix$budget.prepared(System.nanoTime() - start);
    }
  }
}
