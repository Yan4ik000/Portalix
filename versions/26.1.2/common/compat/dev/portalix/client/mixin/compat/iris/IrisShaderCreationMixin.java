package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.portalix.client.compat.iris.IrisScene;
import net.irisshaders.iris.gl.shader.GlShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GlShader.class)
abstract class IrisShaderCreationMixin {
  @ModifyExpressionValue(method = "createShader", at = @At(value = "INVOKE",
      target = "Lcom/mojang/blaze3d/opengl/GlStateManager;glCreateShader(I)I"), allow = 1)
  private static int portalix$trackShader(int shader) {
    return IrisScene.trackShader(shader);
  }
}
