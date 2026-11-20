package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.portalix.client.compat.iris.IrisScene;
import net.irisshaders.iris.gl.shader.ProgramCreator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ProgramCreator.class)
abstract class IrisProgramCreationMixin {
  @ModifyExpressionValue(method = "create", at = @At(value = "INVOKE",
      target = "Lcom/mojang/blaze3d/opengl/GlStateManager;glCreateProgram()I"), allow = 1)
  private static int portalix$trackProgram(int program) {
    return IrisScene.trackProgram(program);
  }
}
