package dev.portalix.client.mixin.clip;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.portalix.client.portal.clip.PortalClipPipelineCache;
import dev.portalix.client.portal.clip.PortalClipping;
import net.minecraft.client.renderer.PostChain;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PostChain.class)
abstract class PostChainPrecompileMixin {
  @WrapOperation(method = "createPass", at = @At(value = "INVOKE", target =
      "Lcom/mojang/blaze3d/systems/RenderSystem;getCompiledPipelineNullable(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline;"),
      require = 1, allow = 1)
  private static @Nullable CompiledRenderPipeline portalix$precompileClip(
      RenderPipeline pipeline, Operation<CompiledRenderPipeline> original) {
    return PortalClipping.without(() -> {
      var ordinary = original.call(pipeline);
      if (ordinary == null) return null;
      var clipped = PortalClipPipelineCache.clipped(pipeline);
      return clipped == pipeline || original.call(clipped) != null ? ordinary : null;
    });
  }
}
