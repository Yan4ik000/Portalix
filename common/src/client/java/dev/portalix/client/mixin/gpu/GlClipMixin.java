package dev.portalix.client.mixin.gpu;

import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.backend.opengl.GlRenderPipeline;
import com.mojang.renderpearl.frontend.FrontendRenderPass;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import dev.portalix.client.portal.clip.GlClipState;
import dev.portalix.client.portal.clip.PortalClipping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FrontendRenderPass.class)
abstract class GlClipMixin {
  @Inject(method = "setPipeline", at = @At("TAIL"))
  private void portalix$clipDistance(CompiledRenderPipeline pipeline, CallbackInfo ci) {
    if (!(pipeline instanceof FrontendRenderPipeline front)
        || !(front.backendRenderPipeline() instanceof GlRenderPipeline)) return;
    GlClipState.setEnabled(PortalClipping.active()
        && front.uniformIndices().containsKey(PortalClipping.UNIFORM_NAME));
  }
}
