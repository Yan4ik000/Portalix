package dev.portalix.client.mixin.terrain.vanilla;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import dev.portalix.client.portal.terrain.PortalFrameTerrainBudget;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
abstract class TerrainExtractionBudgetMixin {
  @Unique private PortalFrameTerrainBudget.Allocation portalix$budget;

  @WrapMethod(method = "compileSections")
  private void portalix$extractionBudget(Camera camera, Operation<Void> original) {
    var budget = PortalFrameTerrainBudget.claim((LevelRenderer) (Object) this);
    portalix$budget = budget;
    try {
      original.call(camera);
    } finally {
      portalix$budget = null;
      budget.finish();
    }
  }

  @WrapOperation(method = "compileSections", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;rebuildSectionSync(Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;Lnet/minecraft/client/renderer/chunk/RenderRegionCache;)V"))
  private void portalix$syncPreparation(SectionRenderDispatcher dispatcher, RenderSection section,
      RenderRegionCache cache, Operation<Void> original, @Share("prepared") LocalBooleanRef prepared) {
    prepared.set(portalix$prepare(section, () -> original.call(dispatcher, section, cache)));
  }

  @WrapOperation(method = "compileSections", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;rebuildSectionAsync(Lnet/minecraft/client/renderer/chunk/RenderRegionCache;)V"))
  private void portalix$asyncPreparation(RenderSection section, RenderRegionCache cache,
      Operation<Void> original, @Share("prepared") LocalBooleanRef prepared) {
    prepared.set(portalix$prepare(section, () -> original.call(section, cache)));
  }

  @WrapWithCondition(method = "compileSections", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;setNotDirty()V"),
      require = 2, allow = 2)
  private boolean portalix$prepared(RenderSection section, @Share("prepared") LocalBooleanRef prepared) {
    return prepared.get();
  }

  @WrapWithCondition(method = "compileSections", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;setWasPreviouslyEmpty(Z)V"))
  private boolean portalix$deferEmptyFlagReset(RenderSection section, boolean previouslyEmpty) {
    return false;
  }

  @Unique
  private boolean portalix$prepare(RenderSection section, Runnable build) {
    if (!section.isDirtyFromPlayer() && !portalix$budget.canPrepare()) return false;
    section.setWasPreviouslyEmpty(false);
    long start = System.nanoTime();
    build.run();
    portalix$budget.prepared(System.nanoTime() - start);
    return true;
  }
}
