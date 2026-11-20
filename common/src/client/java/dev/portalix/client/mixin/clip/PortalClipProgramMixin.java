package dev.portalix.client.mixin.clip;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.portalix.client.portal.clip.PortalClipPipelineCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(RenderSystem.class)
abstract class PortalClipProgramMixin {
  @ModifyVariable(method = "getCompiledPipelineNullable", at = @At("HEAD"), argsOnly = true)
  private static RenderPipeline portalix$clipProgram(RenderPipeline pipeline) {
    return PortalClipPipelineCache.select(pipeline);
  }
}
