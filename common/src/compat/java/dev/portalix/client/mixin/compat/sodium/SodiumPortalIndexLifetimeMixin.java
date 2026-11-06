package dev.portalix.client.mixin.compat.sodium;

import dev.portalix.client.compat.sodium.SodiumDynamicSections;
import dev.portalix.client.compat.sodium.SodiumPortalIndices;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = RenderRegion.class, remap = false)
abstract class SodiumPortalIndexLifetimeMixin {
  @Inject(method = "removeSection", at = @At("HEAD"))
  private void portalix$remove(RenderSection section, CallbackInfo ci) {
    ((SodiumDynamicSections) this).portalix$dynamicSection(section.getSectionIndex(), false);
    SodiumPortalIndices.remove(section);
  }

  @Inject(method = "delete", at = @At("HEAD"))
  private void portalix$delete(CallbackInfo ci) {
    SodiumPortalIndices.remove((RenderRegion) (Object) this);
  }

  @Inject(method = "onIndexSegmentChange", at = @At("HEAD"), cancellable = true)
  private void portalix$relocated(int ownerIndex, CallbackInfo ci) {
    if (ownerIndex != SodiumPortalIndices.OWNER) return;
    ((RenderRegion) (Object) this).clearCachedBatchFor(DefaultTerrainRenderPasses.TRANSLUCENT);
    ci.cancel();
  }
}
