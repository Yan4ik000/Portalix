package dev.portalix.client.mixin.clip;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuDevice;
import dev.portalix.client.portal.clip.PortalClipPipelineCache;
import net.minecraft.client.renderer.PostChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PostChain.class)
abstract class PostChainPrecompileMixin {
  @WrapOperation(
      method = "createPass",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/mojang/blaze3d/systems/GpuDevice;precompilePipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)Lcom/mojang/blaze3d/pipeline/CompiledRenderPipeline;"))
  private static CompiledRenderPipeline portalix$precompileClip(
      GpuDevice device, RenderPipeline pipeline, Operation<CompiledRenderPipeline> original) {
    var ordinary = original.call(device, pipeline);
    var clipped = original.call(device, PortalClipPipelineCache.clipped(pipeline));
    return clipped.isValid() ? ordinary : clipped;
  }
}
