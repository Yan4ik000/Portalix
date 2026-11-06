package dev.portalix.client.mixin.compat.sodium;

import dev.portalix.client.portal.scene.PortalSceneManager;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class SodiumFogMixin {
  @Dynamic("Sodium FogStorage")
  @Inject(method = "sodium$getFogParameters", at = @At("HEAD"), cancellable = true, remap = false)
  private void portalix$fog(CallbackInfoReturnable<FogParameters> cir) {
    var camera = PortalSceneManager.cameraState();
    if (camera == null || camera.fogData == null) return;
    var fog = camera.fogData;
    cir.setReturnValue(
        new FogParameters(
            fog.color.x,
            fog.color.y,
            fog.color.z,
            fog.color.w,
            fog.environmentalStart,
            fog.environmentalEnd,
            fog.renderDistanceStart,
            fog.renderDistanceEnd));
  }
}
