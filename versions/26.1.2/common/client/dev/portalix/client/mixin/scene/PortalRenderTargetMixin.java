package dev.portalix.client.mixin.scene;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.portalix.client.portal.scene.PortalSceneManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class PortalRenderTargetMixin {
  @Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true)
  private void portalix$target(CallbackInfoReturnable<RenderTarget> cir) {
    RenderTarget target = PortalSceneManager.target();
    if (target != null) cir.setReturnValue(target);
  }
}
