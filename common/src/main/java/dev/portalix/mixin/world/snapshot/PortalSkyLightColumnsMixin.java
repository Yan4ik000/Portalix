package dev.portalix.mixin.world.snapshot;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.world.snapshot.SkyLightColumnAccess;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SkyLightSectionStorage.class)
abstract class PortalSkyLightColumnsMixin<M extends DataLayerStorageMap<M>>
    extends LayerLightSectionStorage<M> {
  protected PortalSkyLightColumnsMixin(LightLayer layer, LightChunkGetter source, M data) {
    super(layer, source, data);
  }

  @WrapOperation(
      method = "getLightValue(JZ)I",
      at =
          @At(
              value = "INVOKE",
              target = "Lit/unimi/dsi/fastutil/longs/Long2IntOpenHashMap;get(J)I"))
  private int portalix$readVisible(
      Long2IntOpenHashMap columns,
      long column,
      Operation<Integer> original,
      @Local(argsOnly = true) boolean updating) {
    return ((SkyLightColumnAccess) (updating ? updatingSectionData : visibleSectionData))
        .portalix$getTop(column);
  }

  @WrapOperation(
      method = {"onNodeAdded", "onNodeRemoved", "createDataLayer", "isAboveData", "getTopSectionY"},
      at =
          @At(
              value = "INVOKE",
              target = "Lit/unimi/dsi/fastutil/longs/Long2IntOpenHashMap;get(J)I"))
  private int portalix$readUpdating(
      Long2IntOpenHashMap columns, long column, Operation<Integer> original) {
    return ((SkyLightColumnAccess) updatingSectionData).portalix$getTop(column);
  }

  @WrapOperation(
      method = {"onNodeAdded", "onNodeRemoved"},
      at =
          @At(
              value = "INVOKE",
              target = "Lit/unimi/dsi/fastutil/longs/Long2IntOpenHashMap;put(JI)I"))
  private int portalix$put(
      Long2IntOpenHashMap columns, long column, int top, Operation<Integer> original) {
    return ((SkyLightColumnAccess) updatingSectionData).portalix$putTop(column, top);
  }

  @WrapOperation(
      method = "onNodeRemoved",
      at =
          @At(
              value = "INVOKE",
              target = "Lit/unimi/dsi/fastutil/longs/Long2IntOpenHashMap;remove(J)I"))
  private int portalix$remove(
      Long2IntOpenHashMap columns, long column, Operation<Integer> original) {
    return ((SkyLightColumnAccess) updatingSectionData).portalix$removeTop(column);
  }

  @WrapOperation(
      method = "onNodeAdded",
      at =
          @At(
              value = "INVOKE",
              target = "Lit/unimi/dsi/fastutil/longs/Long2IntOpenHashMap;defaultReturnValue(I)V"))
  private void portalix$bottom(Long2IntOpenHashMap columns, int bottom, Operation<Void> original) {
    ((SkyLightColumnAccess) updatingSectionData).portalix$defaultTop(bottom);
  }
}
