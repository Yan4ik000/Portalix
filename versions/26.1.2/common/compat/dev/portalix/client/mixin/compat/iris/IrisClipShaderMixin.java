package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.portalix.client.compat.iris.IrisPortalShaders;
import dev.portalix.client.compat.iris.IrisScene;
import net.irisshaders.iris.pipeline.programs.PartialShader;
import net.irisshaders.iris.pipeline.programs.ShaderCreator;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ShaderCreator.class)
abstract class IrisClipShaderMixin {
  @ModifyExpressionValue(method = "link", at = @At(value = "INVOKE", target =
      "Lcom/mojang/blaze3d/opengl/GlStateManager;glCreateProgram()I"), allow = 1)
  private static int portalix$trackLinkedProgram(int program) {
    return IrisScene.trackProgram(program);
  }

  @ModifyExpressionValue(method = "createShader", at = @At(value = "INVOKE", target =
      "Lcom/mojang/blaze3d/opengl/GlStateManager;glCreateShader(I)I"), allow = 1)
  private static int portalix$trackLinkedShader(int shader) {
    return IrisScene.trackShader(shader);
  }

  @WrapOperation(
      method = {"create", "createFallback", "createShadow", "createFallbackShadow"},
      require = 4,
      allow = 4,
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/irisshaders/iris/pipeline/programs/ShaderCreator;link(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lcom/mojang/blaze3d/vertex/VertexFormat;Z)Lnet/irisshaders/iris/pipeline/programs/PartialShader;"))
  private static PartialShader portalix$clip(
      String name,
      String vertex,
      String geometry,
      String control,
      String evaluation,
      String fragment,
      VertexFormat format,
      boolean fallback,
      Operation<PartialShader> original,
      @Local(argsOnly = true) ShaderKey key) {
    var program =
        IrisPortalShaders.program(
            vertex, geometry, control, evaluation, fragment, key, fallback);
    return original.call(
        name,
        program.vertex(),
        program.geometry(),
        program.control(),
        program.evaluation(),
        program.fragment(),
        format,
        fallback);
  }
}
