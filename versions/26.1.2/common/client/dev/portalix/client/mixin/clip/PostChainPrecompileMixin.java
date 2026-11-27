package dev.portalix.client.mixin.clip;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.portalix.client.portal.clip.PortalClipPipelineCache;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PostChain.class)
abstract class PostChainPrecompileMixin {
  @ModifyExpressionValue(method = "createPass", at = @At(value = "INVOKE", target =
      "Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;build()Lcom/mojang/blaze3d/pipeline/RenderPipeline;"), allow = 1)
  private static RenderPipeline portalix$precompileClip(RenderPipeline pipeline) throws ShaderManager.CompilationException {
    var device = RenderSystem.getDevice();
    if (!device.precompilePipeline(pipeline).isValid()
        || !device.precompilePipeline(PortalClipPipelineCache.clipped(pipeline)).isValid())
      throw new ShaderManager.CompilationException("Failed to compile post chain pass: " + pipeline.getLocation());
    return pipeline;
  }
}
