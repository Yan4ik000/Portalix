package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.portalix.client.compat.iris.IrisScene;
import net.irisshaders.iris.gl.texture.GlTexture;
import net.irisshaders.iris.pipeline.CustomTextureManager;
import net.irisshaders.iris.targets.backed.NativeImageBackedCustomTexture;
import net.irisshaders.iris.targets.backed.NativeImageBackedNoiseTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CustomTextureManager.class)
abstract class IrisCustomTextureCreationMixin {
  @ModifyExpressionValue(method = "<init>", at = @At(value = "NEW", target =
      "net/irisshaders/iris/targets/backed/NativeImageBackedNoiseTexture"), allow = 1)
  private NativeImageBackedNoiseTexture portalix$trackNoise(NativeImageBackedNoiseTexture texture) {
    IrisScene.trackResource(texture::close);
    return texture;
  }

  @ModifyExpressionValue(method = "createCustomTexture", at = @At(value = "NEW", target =
      "net/irisshaders/iris/targets/backed/NativeImageBackedCustomTexture"), allow = 1)
  private NativeImageBackedCustomTexture portalix$trackPng(NativeImageBackedCustomTexture texture) {
    IrisScene.trackResource(texture::close);
    return texture;
  }

  @ModifyExpressionValue(method = "createCustomTexture", at = @At(value = "NEW", target =
      "net/irisshaders/iris/gl/texture/GlTexture"), require = 4, allow = 4)
  private GlTexture portalix$trackRawTexture(GlTexture texture) {
    IrisScene.trackResource(texture::destroy);
    return texture;
  }
}
