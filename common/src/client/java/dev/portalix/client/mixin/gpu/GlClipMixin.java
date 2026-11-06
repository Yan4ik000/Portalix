package dev.portalix.client.mixin.gpu;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.portal.clip.PortalClipping;
import org.lwjgl.opengl.GL33C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlRenderPass")
abstract class GlClipMixin {
  @Inject(method = "setPipeline", at = @At("TAIL"))
  private void portalix$clipDistance(RenderPipeline pipeline, CallbackInfo ci) {
    if (PortalClipping.active()
        && (pipeline.getShaderDefines().flags().contains("PORTALIX_CLIP")
            || IrisShaders.overridesGeometry(pipeline))) {
      GL33C.glEnable(GL33C.GL_CLIP_DISTANCE0);
      GL33C.glEnable(GL33C.GL_CLIP_DISTANCE1);
    } else {
      GL33C.glDisable(GL33C.GL_CLIP_DISTANCE0);
      GL33C.glDisable(GL33C.GL_CLIP_DISTANCE1);
    }
  }
}
