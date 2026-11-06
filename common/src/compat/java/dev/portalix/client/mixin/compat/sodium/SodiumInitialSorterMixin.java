package dev.portalix.client.mixin.compat.sodium;

import dev.portalix.client.compat.sodium.SodiumInitialSorterAccess;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.DynamicBSPData;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.DynamicSorter;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.DynamicTopoData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
    value = {DynamicBSPData.class, DynamicTopoData.class},
    remap = false)
abstract class SodiumInitialSorterMixin {
  @Inject(method = "getSorter", at = @At("RETURN"))
  private void portalix$initial(boolean initial, CallbackInfoReturnable<DynamicSorter> cir) {
    ((SodiumInitialSorterAccess) cir.getReturnValue()).portalix$initial(initial);
  }
}
