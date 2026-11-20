package dev.portalix.client.mixin.compat.iris;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.portalix.client.compat.iris.IrisScene;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
abstract class IrisPrewarmTargetMixin {
  @Inject(method = "mainRenderTarget", at = @At("HEAD"), cancellable = true)
  private void portalix$prewarmTarget(CallbackInfoReturnable<RenderTarget> cir) {
    var target = IrisScene.prewarmTarget();
    if (target != null) cir.setReturnValue(target);
  }
}
