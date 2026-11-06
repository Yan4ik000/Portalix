package dev.portalix.mixin.world.snapshot;

import dev.portalix.world.snapshot.LightSectionStorageAccess;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
    targets = {
      "net.minecraft.world.level.lighting.BlockLightSectionStorage$BlockDataLayerStorageMap",
      "net.minecraft.world.level.lighting.SkyLightSectionStorage$SkyDataLayerStorageMap"
    })
abstract class PortalLightSnapshotMixin {
  @Inject(
      method = {
        "copy()Lnet/minecraft/world/level/lighting/BlockLightSectionStorage$BlockDataLayerStorageMap;",
        "copy()Lnet/minecraft/world/level/lighting/SkyLightSectionStorage$SkyDataLayerStorageMap;"
      },
      at = @At("RETURN"))
  private void portalix$snapshot(CallbackInfoReturnable<DataLayerStorageMap<?>> callback) {
    var source = (LightSectionStorageAccess) this;
    var snapshot = (LightSectionStorageAccess) callback.getReturnValue();
    snapshot.portalix$lightSections(source.portalix$lightSections().copy());
  }
}
