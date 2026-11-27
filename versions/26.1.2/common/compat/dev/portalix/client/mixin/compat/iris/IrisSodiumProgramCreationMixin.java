package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.portalix.client.compat.iris.IrisScene;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = GlProgram.Builder.class, remap = false)
abstract class IrisSodiumProgramCreationMixin {
  @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE",
      target = "Lorg/lwjgl/opengl/GL20C;glCreateProgram()I"), allow = 1)
  private int portalix$trackProgram(int program) {
    return IrisScene.trackProgram(program);
  }
}
