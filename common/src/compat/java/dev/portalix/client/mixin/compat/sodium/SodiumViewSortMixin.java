package dev.portalix.client.mixin.compat.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger.CameraMovement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = RenderSectionManager.class, remap = false)
abstract class SodiumViewSortMixin {
  @Inject(method = "processGFNIMovement", at = @At("HEAD"), cancellable = true)
  private void portalix$viewMovement(CameraMovement movement, CallbackInfo ci) {
    ci.cancel();
  }

  @Inject(method = "scheduleSort", at = @At("HEAD"), cancellable = true)
  private void portalix$viewSort(long section, boolean direct, CallbackInfo ci) {
    ci.cancel();
  }
}
