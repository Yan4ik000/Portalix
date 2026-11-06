package dev.portalix.client.mixin.compat.sodium;

import dev.portalix.client.compat.sodium.SodiumInitialSort;
import dev.portalix.client.compat.sodium.SodiumPortalIndices;
import dev.portalix.client.compat.sodium.SodiumSortPlanes;
import dev.portalix.client.compat.sodium.SodiumSortPlanesAccess;
import dev.portalix.client.compat.sodium.SodiumTopoQuadsAccess;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.DynamicData;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.DynamicTopoData;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger.GeometryPlanes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = DynamicData.class, remap = false)
abstract class SodiumSortPlanesMixin implements SodiumSortPlanesAccess {
  @Shadow protected GeometryPlanes geometryPlanes;
  @Unique private SodiumSortPlanes portalix$planes;
  @Unique private SodiumInitialSort portalix$initial;

  @Inject(method = "discardGeometryPlanesAfterIntegration", at = @At("HEAD"))
  private void portalix$retainPartitions(CallbackInfo ci) {
    portalix$retainSortPlanes(geometryPlanes);
  }

  @Override
  public SodiumSortPlanes portalix$sortPlanes() {
    return portalix$planes;
  }

  @Override
  public SodiumInitialSort portalix$initialSort() {
    return portalix$initial;
  }

  @Override
  public void portalix$initialSort(SodiumInitialSort initial) {
    portalix$initial = initial;
    SodiumPortalIndices.geometryChanged();
  }

  @Override
  public void portalix$retainSortPlanes(GeometryPlanes planes) {
    if ((Object) this instanceof DynamicTopoData topo && !topo.GFNITriggerEnabled()) return;
    if (portalix$planes == null && planes != null)
      portalix$planes =
          new SodiumSortPlanes(
              planes,
              (Object) this instanceof SodiumTopoQuadsAccess topo ? topo.portalix$quads() : null);
  }
}
