package dev.portalix.client.mixin.compat.sodium;

import dev.portalix.client.compat.sodium.SodiumDynamicSections;
import dev.portalix.client.compat.sodium.SodiumPortalIndices;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.DynamicData;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.TranslucentData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = RenderSection.class, remap = false)
abstract class SodiumViewIndexDataMixin {
  @Inject(method = "setTranslucentData", at = @At("HEAD"))
  private void portalix$meshChanged(TranslucentData next, CallbackInfo ci) {
    var section = (RenderSection) (Object) this;
    if (section.getTranslucentData() != next) SodiumPortalIndices.remove(section);
  }

  @Inject(method = "setTranslucentData", at = @At("RETURN"))
  private void portalix$dynamicSection(TranslucentData next, CallbackInfo ci) {
    var section = (RenderSection) (Object) this;
    ((SodiumDynamicSections) section.getRegion())
        .portalix$dynamicSection(section.getSectionIndex(), next instanceof DynamicData);
  }
}
