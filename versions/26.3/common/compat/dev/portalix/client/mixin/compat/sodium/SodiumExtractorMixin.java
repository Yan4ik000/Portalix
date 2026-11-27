package dev.portalix.client.mixin.compat.sodium;

import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LevelExtractor.class)
abstract class SodiumExtractorMixin {
  @ModifyArg(
      method = "allChanged",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/SectionUpdateTracker;<init>(Lnet/minecraft/world/level/LevelHeightAccessor;I)V"))
  private int portalix$unusedVanillaTracker(int distance) {

    return 0;
  }
}
