package dev.portalix.client.mixin.compat.iris;

import dev.portalix.client.compat.iris.IrisScene;
import dev.portalix.client.compat.iris.IrisPortalShaders;
import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PipelineManager.class)
abstract class IrisPortalPipelineMixin {
  @Inject(method = "preparePipeline", at = @At("RETURN"))
  private void portalix$prepareViews(
      NamespacedId dimension, CallbackInfoReturnable<WorldRenderingPipeline> cir) {
    IrisScene.prepare((PipelineManager) (Object) this, dimension, cir.getReturnValue());
  }

  @Inject(method = "destroyPipeline", at = @At("TAIL"))
  private void portalix$shaderCache(CallbackInfo ci) {
    IrisPortalShaders.clear();
  }

  @Inject(method = "preparePipeline", at = @At("HEAD"), cancellable = true)
  private void portalix$scenePipeline(
      NamespacedId dimension, CallbackInfoReturnable<WorldRenderingPipeline> cir) {
    var pipeline = IrisScene.current();
    if (pipeline != null) cir.setReturnValue(pipeline);
  }
}
