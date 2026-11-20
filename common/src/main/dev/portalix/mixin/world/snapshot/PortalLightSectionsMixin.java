package dev.portalix.mixin.world.snapshot;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.world.snapshot.LightSectionSnapshot;
import dev.portalix.world.snapshot.LightSectionStorageAccess;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DataLayerStorageMap.class)
abstract class PortalLightSectionsMixin implements LightSectionStorageAccess {
  @Shadow @Final @Mutable protected Long2ObjectOpenHashMap<DataLayer> map;
  @Unique private LightSectionSnapshot portalix$sections;

  @Inject(method = "<init>", at = @At("RETURN"))
  private void portalix$storage(Long2ObjectOpenHashMap<DataLayer> initial, CallbackInfo ci) {
    portalix$sections = new LightSectionSnapshot(initial);

    map = new Long2ObjectOpenHashMap<>(0);
  }

  @Override
  public LightSectionSnapshot portalix$lightSections() {
    return portalix$sections;
  }

  @Override
  public void portalix$lightSections(LightSectionSnapshot sections) {
    portalix$sections = sections;
  }

  @WrapOperation(
      method = {"getLayer", "copyDataLayer"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;get(J)Ljava/lang/Object;"))
  private Object portalix$get(
      Long2ObjectOpenHashMap<DataLayer> unused, long section, Operation<Object> original) {
    return portalix$sections.get(section);
  }

  @WrapOperation(
      method = {"setLayer", "copyDataLayer"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;put(JLjava/lang/Object;)Ljava/lang/Object;"))
  private Object portalix$put(
      Long2ObjectOpenHashMap<DataLayer> unused,
      long section,
      Object layer,
      Operation<Object> original) {
    return portalix$sections.put(section, (DataLayer) layer);
  }

  @WrapOperation(
      method = "removeLayer",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;remove(J)Ljava/lang/Object;"))
  private Object portalix$remove(
      Long2ObjectOpenHashMap<DataLayer> unused, long section, Operation<Object> original) {
    return portalix$sections.remove(section);
  }

  @WrapOperation(
      method = "hasLayer",
      at =
          @At(
              value = "INVOKE",
              target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;containsKey(J)Z"))
  private boolean portalix$contains(
      Long2ObjectOpenHashMap<DataLayer> unused, long section, Operation<Boolean> original) {
    return portalix$sections.contains(section);
  }
}
