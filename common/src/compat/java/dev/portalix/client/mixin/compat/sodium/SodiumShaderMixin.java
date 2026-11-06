package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.portalix.client.portal.clip.PortalClipPipelineCache;
import dev.portalix.client.portal.clip.PortalClipping;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
    targets = "net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer",
    remap = false)
abstract class SodiumShaderMixin {
  @Shadow
  protected abstract RenderPipeline compileProgram(TerrainRenderPass pass);

  @WrapOperation(
      method = "createShader",
      at = @At(value = "INVOKE", target =
          "Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;build()Lcom/mojang/blaze3d/pipeline/RenderPipeline;"),
      allow = 1)
  private RenderPipeline portalix$terrainLayout(
      RenderPipeline.Builder builder, Operation<RenderPipeline> original) {
    return original.call(builder.withBindGroupLayout(PortalClipping.LAYOUT));
  }

  @Inject(method = "<init>", at = @At("TAIL"))
  private void portalix$preparePrograms(CallbackInfo ci) {
    var device = RenderSystem.getDevice();
    for (var pass : DefaultTerrainRenderPasses.ALL) {
      var pipeline = compileProgram(pass);
      for (var program : new RenderPipeline[] {pipeline, PortalClipPipelineCache.clipped(pipeline)})
        if (!device.precompilePipeline(program).isValid())
          throw new IllegalStateException("Failed to compile portal terrain pipeline: " + program);
    }
  }
}
