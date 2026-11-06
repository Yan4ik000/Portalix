package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.compat.sodium.SodiumInitialSorterAccess;
import dev.portalix.client.compat.sodium.SodiumSortPlanesAccess;
import java.util.function.BiConsumer;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.DynamicData;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.DynamicSorter;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.DynamicTopoData;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.Sorter;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.TranslucentData;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger.CameraMovement;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger.GeometryPlanes;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger.SortTriggering;
import net.minecraft.core.SectionPos;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = SortTriggering.class, remap = false)
abstract class SodiumViewTriggersMixin {
  @Inject(method = "integrateTranslucentData", at = @At("HEAD"))
  private void portalix$viewData(
      TranslucentData previous, TranslucentData next, Sorter sorter,
      Vector3dc camera, BiConsumer<Long, Boolean> callback, CallbackInfo ci) {
    if (next instanceof DynamicData dynamic
        && sorter instanceof SodiumInitialSorterAccess initial) {
      var order = initial.portalix$initialSort();
      if (order != null) ((SodiumSortPlanesAccess) dynamic).portalix$initialSort(order);
    }
    if (previous != next && next instanceof DynamicData dynamic)
      ((SodiumSortPlanesAccess) dynamic)
          .portalix$retainSortPlanes(((DynamicSorter) sorter).getGeometryPlanes());
  }

  // Each portal camera owns its triggers; Sodium still owns data integration and counters.
  @WrapOperation(
      method = {"integrateTranslucentData", "removeSection", "applyTopoSortingTriggerChanges"},
      at = {
        @At(value = "INVOKE", target =
            "Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/trigger/GFNITriggers;removeSection(JLnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/data/TranslucentData;)V"),
        @At(value = "INVOKE", target =
            "Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/trigger/DirectTriggers;removeSection(JLnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/data/TranslucentData;)V")
      })
  private void portalix$viewRemoval(
      @Coerce Object triggers, long position, TranslucentData data, Operation<Void> original) {}

  @WrapOperation(
      method = "integrateTranslucentData",
      at = @At(value = "INVOKE", target =
          "Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/trigger/GFNITriggers;integrateSection(Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/trigger/SortTriggering;Lnet/minecraft/core/SectionPos;Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/trigger/GeometryPlanes;Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/trigger/CameraMovement;)V"))
  private void portalix$viewPlanes(
      @Coerce Object triggers, SortTriggering owner, SectionPos position,
      GeometryPlanes planes, CameraMovement movement, Operation<Void> original) {}

  @WrapOperation(
      method = {"integrateTranslucentData", "applyTopoSortingTriggerChanges"},
      at = @At(value = "INVOKE", target =
          "Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/trigger/DirectTriggers;integrateSection(Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/trigger/SortTriggering;Lnet/minecraft/core/SectionPos;Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/data/DynamicTopoData;Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/trigger/CameraMovement;)V"))
  private void portalix$viewDirectTriggers(
      @Coerce Object triggers, SortTriggering owner, SectionPos position,
      DynamicTopoData data, CameraMovement movement, Operation<Void> original) {}
}
