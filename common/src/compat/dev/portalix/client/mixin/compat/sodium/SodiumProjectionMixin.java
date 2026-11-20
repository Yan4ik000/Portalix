package dev.portalix.client.mixin.compat.sodium;

import dev.portalix.client.portal.scene.PortalSceneManager;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
abstract class SodiumProjectionMixin {
  @Dynamic("Sodium GameRendererStorage")
  @Inject(
      method = "sodium$getProjectionMatrix",
      at = @At("HEAD"),
      cancellable = true,
      remap = false)
  private void portalix$projection(CallbackInfoReturnable<Matrix4fc> cir) {
    var camera = PortalSceneManager.cameraState();
    if (camera != null) cir.setReturnValue(camera.projectionMatrix);
  }
}
