package dev.portalix.client.mixin.clip;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import dev.portalix.client.portal.clip.PortalClipPipelineCache;
import dev.portalix.client.portal.clip.PortalClipping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderPass.class)
public abstract class PortalClipRenderPassMixin {
  @ModifyVariable(method = "setPipeline", at = @At("HEAD"), argsOnly = true)
  private RenderPipeline portalix$clipProgram(RenderPipeline pipeline) {
    return PortalClipPipelineCache.select(pipeline);
  }

  @Inject(method = "setPipeline", at = @At("TAIL"))
  private void portalix$clip(RenderPipeline pipeline, CallbackInfo ci) {
    if (PortalClipPipelineCache.usesClipUniform(pipeline))
      ((RenderPass) (Object) this).setUniform("PortalClip", PortalClipping.uniform());
  }
}
