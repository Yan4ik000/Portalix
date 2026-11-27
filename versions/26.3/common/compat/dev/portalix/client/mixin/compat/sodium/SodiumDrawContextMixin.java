package dev.portalix.client.mixin.compat.sodium;

import net.caffeinemc.mods.sodium.client.gpu.device.backend.DrawBackend;
import net.caffeinemc.mods.sodium.client.gpu.device.context.DrawContext;
import net.caffeinemc.mods.sodium.client.gpu.device.context.VKMultiDrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(value = DrawContext.class, remap = false)
abstract class SodiumDrawContextMixin {
  @Inject(method = "create", at = @At("HEAD"), cancellable = true)
  private static void portalix$transientCommands(CallbackInfoReturnable<DrawContext> cir) {
    // Indirect commands use the device's transient allocator, so only push constants remain here.
    if (DrawBackend.BACKEND == DrawBackend.VK_INDIRECT)
      cir.setReturnValue(new VKMultiDrawContext());
  }
}
