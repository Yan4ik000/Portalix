package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.portalix.client.compat.iris.IrisScene;
import net.irisshaders.iris.gl.image.GlImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GlImage.class)
abstract class IrisImageCreationMixin {
  @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE", target =
      "Lnet/irisshaders/iris/gl/IrisRenderSystem;createTexture(I)I"), allow = 1)
  private static int portalix$trackImage(int texture) {
    return IrisScene.trackTexture(texture);
  }
}
