package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.portalix.client.compat.iris.IrisScene;
import net.caffeinemc.mods.sodium.client.gl.shader.GlShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = GlShader.class, remap = false)
abstract class IrisSodiumShaderCreationMixin {
  @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE",
      target = "Lorg/lwjgl/opengl/GL20C;glCreateShader(I)I"), allow = 1)
  private int portalix$trackShader(int shader) {
    return IrisScene.trackShader(shader);
  }
}
