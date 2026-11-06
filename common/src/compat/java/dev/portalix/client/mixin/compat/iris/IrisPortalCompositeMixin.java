package dev.portalix.client.mixin.compat.iris;

import dev.portalix.client.compat.iris.IrisEffectFramebuffer;
import dev.portalix.client.compat.iris.IrisPortalComposite;
import dev.portalix.client.compat.iris.IrisScene;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IrisRenderingPipeline.class)
abstract class IrisPortalCompositeMixin {
  @Inject(
      method = "beginTranslucents",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/irisshaders/iris/pipeline/CompositeRenderer;renderAll()V"))
  private void portalix$restoreWorldDepth(CallbackInfo ci) {
    IrisPortalComposite.restoreDepth();
  }

  @Inject(method = "beginHand", at = @At("HEAD"))
  private void portalix$apertures(CallbackInfo ci) {
    IrisPortalComposite.beforeHand((IrisRenderingPipelineAccessor) this);
  }

  @Inject(method = "destroy", at = @At("HEAD"))
  private void portalix$release(CallbackInfo ci) {
    IrisEffectFramebuffer.release((WorldRenderingPipeline) this);
    IrisScene.release((WorldRenderingPipeline) this);
    IrisPortalComposite.release((IrisRenderingPipelineAccessor) this);
  }

  @Inject(method = "finalizeLevelRendering", at = @At("TAIL"))
  private void portalix$composite(CallbackInfo ci) {
    IrisPortalComposite.finish();
  }
}
