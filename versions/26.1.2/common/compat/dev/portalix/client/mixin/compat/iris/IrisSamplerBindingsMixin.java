package dev.portalix.client.mixin.compat.iris;

import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.opengl.Uniform;
import com.mojang.blaze3d.systems.RenderPassBackend;
import java.util.HashMap;
import net.irisshaders.iris.pipeline.programs.ExtendedShader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlRenderPass")
abstract class IrisSamplerBindingsMixin implements RenderPassBackend {
  @Shadow protected GlRenderPipeline pipeline;
  @Shadow @Final protected HashMap<String, ?> samplers;

  @Inject(method = {"draw", "drawIndexed", "drawMultipleIndexed"}, at = @At("HEAD"))
  private void portalix$irisDefaultTextures(CallbackInfo ci) {
    if (pipeline == null || !(pipeline.program() instanceof ExtendedShader shader)) return;
    // Iris provides these defaults after the encoder validates the sampler bindings.
    var uniforms = shader.getUniforms();
    var iris = ((IrisExtendedShaderAccessor) shader).portalix$pipeline();
    if (!samplers.containsKey("Sampler1") && uniforms.get("Sampler1") instanceof Uniform.Sampler) {
      var overlay = iris.getBiggerWhitePixel();
      bindTexture("Sampler1", overlay.getTextureView(), overlay.getSampler());
    }
    if (!samplers.containsKey("Sampler2") && uniforms.get("Sampler2") instanceof Uniform.Sampler) {
      var light = iris.getWhitePixel();
      bindTexture("Sampler2", light.getTextureView(), light.getSampler());
    }
  }
}
