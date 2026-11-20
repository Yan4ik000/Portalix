package dev.portalix.client.mixin.compat.iris;

import dev.portalix.client.compat.iris.IrisEffectFramebuffer;
import dev.portalix.client.compat.iris.IrisPortalComposite;
import dev.portalix.client.compat.iris.IrisScene;
import net.irisshaders.iris.gl.program.ComputeProgram;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IrisRenderingPipeline.class)
abstract class IrisPortalCompositeMixin {
  @Shadow @Final private CompositeRenderer beginRenderer;
  @Shadow @Final private ComputeProgram[] setup;
  @Shadow @Final private ComputeProgram[] shadowComputes;

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
    // Iris owns these programs but its destroy method omits these three stages.
    beginRenderer.destroy();
    portalix$destroyComputes(setup);
    portalix$destroyComputes(shadowComputes);
    IrisEffectFramebuffer.release((WorldRenderingPipeline) this);
    IrisScene.release((WorldRenderingPipeline) this);
    IrisPortalComposite.release((IrisRenderingPipelineAccessor) this);
  }

  @Unique
  private static void portalix$destroyComputes(ComputeProgram[] programs) {
    for (var program : programs) if (program != null) program.destroy();
  }

  @Inject(method = "finalizeLevelRendering", at = @At("TAIL"))
  private void portalix$composite(CallbackInfo ci) {
    IrisPortalComposite.finish();
  }
}
