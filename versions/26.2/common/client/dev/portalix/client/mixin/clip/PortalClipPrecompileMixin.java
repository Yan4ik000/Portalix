package dev.portalix.client.mixin.clip;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.GpuDevice;
import dev.portalix.client.portal.clip.PortalClipPipelineCache;
import net.minecraft.client.renderer.ShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShaderManager.class)
abstract class PortalClipPrecompileMixin {
  @Inject(method = "apply", at = @At("HEAD"))
  private void portalix$reload(CallbackInfo ci) {
    PortalClipPipelineCache.clear();
  }

  @WrapOperation(
      method = "apply",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/mojang/blaze3d/systems/GpuDevice;precompilePipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/shaders/ShaderSource;)Lcom/mojang/blaze3d/pipeline/CompiledRenderPipeline;"))
  private CompiledRenderPipeline portalix$precompileClip(
      GpuDevice device,
      RenderPipeline pipeline,
      ShaderSource sources,
      Operation<CompiledRenderPipeline> original) {
    var ordinary = original.call(device, pipeline, sources);
    var clipped = original.call(device, PortalClipPipelineCache.clipped(pipeline), sources);
    return clipped.isValid() ? ordinary : clipped;
  }
}
