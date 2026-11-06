package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.portalix.client.portal.scene.PortalSceneManager;
import net.irisshaders.iris.uniforms.BiomeUniforms;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BiomeUniforms.class)
abstract class IrisBiomeUniformsMixin {
  // BiomeUniforms reads the position inside compiler-generated supplier methods.
  @ModifyExpressionValue(
      method = "*",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/player/LocalPlayer;blockPosition()Lnet/minecraft/core/BlockPos;"))
  private static BlockPos portalix$viewBiome(BlockPos original) {
    var eye = PortalSceneManager.environmentPosition();
    return eye == null ? original : BlockPos.containing(eye);
  }
}
