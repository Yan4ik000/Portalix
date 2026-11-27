package dev.portalix.client.mixin.compat.iris;

import com.mojang.blaze3d.pipeline.RenderPipeline.UniformDescription;
import dev.portalix.client.portal.clip.PortalClipping;
import java.util.ArrayList;
import java.util.List;
import net.irisshaders.iris.pipeline.programs.ExtendedShader;
import net.irisshaders.iris.pipeline.programs.FallbackShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin({ExtendedShader.class, FallbackShader.class})
abstract class IrisClipBindingsMixin {
  @ModifyArg(
      method = "<init>",
      at = {
        @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/opengl/GlProgram;setupUniforms(Ljava/util/List;Ljava/util/List;)V"),
        @At(value = "INVOKE", target =
            "Lnet/irisshaders/iris/pipeline/programs/FallbackShader;setupUniforms(Ljava/util/List;Ljava/util/List;)V")
      },
      index = 0,
      allow = 1)
  private List<UniformDescription> portalix$clipBinding(List<UniformDescription> uniforms) {
    var result = new ArrayList<>(uniforms);
    result.add(PortalClipping.UNIFORM);
    return result;
  }
}
