package dev.portalix.client.mixin.clip;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.backend.api.SpvModule;
import com.mojang.renderpearl.frontend.shaders.GlslCompiler;
import com.mojang.renderpearl.frontend.shaders.PipelineBuilder;
import dev.portalix.client.compat.sodium.SodiumShaderAbi;
import dev.portalix.client.portal.clip.PortalClipShaders;
import net.minecraft.client.renderer.ShaderDefines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PipelineBuilder.class)
abstract class PortalClipShaderMixin {
  @WrapOperation(method = "generateBackendCreateInfo", at = @At(value = "INVOKE",
      target = "Lcom/mojang/renderpearl/frontend/shaders/GlslCompiler;compileToSpv(Ljava/lang/String;Ljava/lang/String;Lcom/mojang/renderpearl/api/pipeline/ShaderType;Lnet/minecraft/client/renderer/ShaderDefines;Lcom/mojang/renderpearl/api/pipeline/ShaderSource;)Lcom/mojang/renderpearl/backend/api/SpvModule;"))
  private SpvModule portalix$clip(
      GlslCompiler compiler, String name, String source, ShaderType type, ShaderDefines defines,
      ShaderSource shaders, Operation<SpvModule> original, @Local(argsOnly = true) RenderPipeline pipeline) {
    var shader = pipeline.getShaders().get(type);
    if (defines.flags().contains("PORTALIX_CLIP")) {
      var vertex = pipeline.getShaders().get(ShaderType.VERTEX);
      int location = PortalClipShaders.firstVaryingLocation(shaders.getShader(vertex, ShaderType.VERTEX));
      source = PortalClipShaders.shader(shader, source, type, location);
    } else if (type == ShaderType.VERTEX && SodiumShaderAbi.terrain(shader)) {
      source = PortalClipShaders.shader(shader, source, type, -1);
    }
    return original.call(compiler, name, source, type, defines, shaders);
  }
}
