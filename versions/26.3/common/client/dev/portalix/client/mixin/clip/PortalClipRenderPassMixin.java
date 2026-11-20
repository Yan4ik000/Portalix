package dev.portalix.client.mixin.clip;

import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.frontend.FrontendRenderPass;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import dev.portalix.client.portal.clip.PortalClipping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FrontendRenderPass.class)
public abstract class PortalClipRenderPassMixin {
  @Inject(method = "setPipeline", at = @At("TAIL"))
  private void portalix$clip(CompiledRenderPipeline pipeline, CallbackInfo ci) {
    if (pipeline instanceof FrontendRenderPipeline front
        && front.uniformIndices().containsKey(PortalClipping.UNIFORM_NAME))
      ((FrontendRenderPass) (Object) this)
          .setUniform(PortalClipping.UNIFORM_NAME, PortalClipping.uniform());
  }
}
