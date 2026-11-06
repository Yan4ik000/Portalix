package dev.portalix.mixin.world.snapshot;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.portalix.world.snapshot.SectionLightUpdates;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LayerLightSectionStorage.class)
abstract class PortalLightUpdateSetMixin {
  @Shadow @Final @Mutable protected LongSet sectionsAffectedByLightUpdates;

  @Inject(method = "<init>", at = @At("RETURN"))
  private void portalix$columnUpdates(CallbackInfo ci) {
    sectionsAffectedByLightUpdates = new SectionLightUpdates();
  }

  @WrapMethod(method = "markSectionAndNeighborsAsAffected")
  private void portalix$markColumnUpdates(long section, Operation<Void> original) {
    ((SectionLightUpdates) sectionsAffectedByLightUpdates).addNeighbors(section);
  }
}
