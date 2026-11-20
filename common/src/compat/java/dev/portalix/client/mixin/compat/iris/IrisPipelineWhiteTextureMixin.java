package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.portalix.client.compat.iris.IrisScene;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.targets.backed.NativeImageBackedSingleColorTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(IrisRenderingPipeline.class)
abstract class IrisPipelineWhiteTextureMixin {
  @ModifyExpressionValue(method = "<init>", at = @At(value = "NEW", target =
      "net/irisshaders/iris/targets/backed/NativeImageBackedSingleColorTexture"), require = 2, allow = 2)
  private NativeImageBackedSingleColorTexture portalix$trackPixel(NativeImageBackedSingleColorTexture texture) {
    IrisScene.trackResource(texture::close);
    return texture;
  }
}
