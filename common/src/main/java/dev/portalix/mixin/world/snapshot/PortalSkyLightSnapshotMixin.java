package dev.portalix.mixin.world.snapshot;

import dev.portalix.world.snapshot.SkyLightColumnAccess;
import dev.portalix.world.snapshot.SkyLightColumnSnapshot;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.lighting.SkyLightSectionStorage$SkyDataLayerStorageMap")
abstract class PortalSkyLightSnapshotMixin implements SkyLightColumnAccess {
  @Unique private SkyLightColumnSnapshot portalix$columns;

  @Inject(method = "<init>", at = @At("RETURN"))
  private void portalix$columns(
      Long2ObjectOpenHashMap<DataLayer> layers,
      Long2IntOpenHashMap columns,
      int bottom,
      CallbackInfo ci) {
    portalix$columns = new SkyLightColumnSnapshot(columns);
  }

  @Inject(
      method =
          "copy()Lnet/minecraft/world/level/lighting/SkyLightSectionStorage$SkyDataLayerStorageMap;",
      at = @At("RETURN"))
  private void portalix$copy(CallbackInfoReturnable<DataLayerStorageMap<?>> ci) {
    ((SkyLightColumnAccess) ci.getReturnValue()).portalix$copyColumnsFrom(this);
  }

  @Override
  public void portalix$copyColumnsFrom(SkyLightColumnAccess source) {
    portalix$columns = ((PortalSkyLightSnapshotMixin) (Object) source).portalix$columns.copy();
  }

  @Override
  public int portalix$getTop(long column) {
    return portalix$columns.get(column);
  }

  @Override
  public int portalix$putTop(long column, int top) {
    return portalix$columns.put(column, top);
  }

  @Override
  public int portalix$removeTop(long column) {
    return portalix$columns.remove(column);
  }

  @Override
  public void portalix$defaultTop(int bottom) {
    portalix$columns.defaultReturnValue(bottom);
  }
}
