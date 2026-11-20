package dev.portalix.client.mixin.compat.sodium;

import dev.portalix.client.compat.sodium.SodiumIndexSortResult;
import dev.portalix.client.compat.sodium.SodiumInitialSort;
import dev.portalix.client.compat.sodium.SodiumInitialSorterAccess;
import dev.portalix.client.compat.sodium.SodiumSortPlanesAccess;
import dev.portalix.client.compat.sodium.SodiumTopoSorterAccess;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.CombinedCameraPos;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.DynamicData;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.DynamicSorter;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger.GeometryPlanes;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = DynamicSorter.class, remap = false)
abstract class SodiumSorterPlanesMixin implements SodiumInitialSorterAccess {
  @Unique private boolean portalix$initial;
  @Unique private SodiumInitialSort portalix$initialSort;

  @Inject(method = "<init>", at = @At("RETURN"))
  private void portalix$meshPartitions(
      int quadCount, DynamicData source, GeometryPlanes geometry, CallbackInfo ci) {
    ((SodiumSortPlanesAccess) source).portalix$retainSortPlanes(geometry);
  }

  @Override
  public void portalix$initial(boolean initial) {
    portalix$initial = initial;
  }

  @Override
  public SodiumInitialSort portalix$initialSort() {
    return portalix$initialSort;
  }

  @Inject(method = "writeIndexBuffer", at = @At("RETURN"))
  private void portalix$captureInitial(CombinedCameraPos camera, CallbackInfo ci) {
    if (!portalix$initial) return;
    var buffer = ((DynamicSorter) (Object) this).getIndexBuffer().getDirectBuffer().duplicate();
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    var position = camera.getAbsoluteCameraPos();
    portalix$initialSort =
        new SodiumInitialSort(
            new Vec3(position.x(), position.y(), position.z()),
            new SodiumIndexSortResult(
                bytes,
                (Object) this instanceof SodiumTopoSorterAccess topo
                    ? topo.portalix$state()
                    : null));
  }
}
