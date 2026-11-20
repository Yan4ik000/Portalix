package dev.portalix.client.mixin.compat.iris;

import dev.portalix.client.compat.iris.IrisScene;
import net.irisshaders.iris.pathways.HorizonRenderer;
import net.irisshaders.iris.pipeline.FinalPassRenderer;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {RenderTargets.class, ShadowRenderTargets.class, HorizonRenderer.class,
    FinalPassRenderer.class}, remap = false)
abstract class IrisPipelineTargetsMixin {
  @Shadow(remap = false) public abstract void destroy();

  @Inject(method = "<init>", at = @At("RETURN"))
  private void portalix$trackTargets(CallbackInfo ci) {
    IrisScene.trackResource(this::destroy);
  }
}
