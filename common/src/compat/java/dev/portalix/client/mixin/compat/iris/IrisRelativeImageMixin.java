package dev.portalix.client.mixin.compat.iris;

import dev.portalix.client.compat.iris.IrisScene;
import net.irisshaders.iris.gl.image.GlImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(GlImage.Relative.class)
abstract class IrisRelativeImageMixin {
  @ModifyArgs(method = "<init>", at = @At(value = "INVOKE", target =
      "Lnet/irisshaders/iris/gl/image/GlImage;<init>(Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/gl/texture/TextureType;Lnet/irisshaders/iris/gl/texture/PixelFormat;Lnet/irisshaders/iris/gl/texture/InternalTextureFormat;Lnet/irisshaders/iris/gl/texture/PixelType;ZIII)V"),
      allow = 1)
  private static void portalix$initialDimensions(Args args) {
    args.set(7, IrisScene.prewarming() ? 1 : Math.max(1, args.<Integer>get(7)));
    args.set(8, IrisScene.prewarming() ? 1 : Math.max(1, args.<Integer>get(8)));
  }

  @ModifyArgs(method = "updateNewSize", at = @At(value = "INVOKE", target =
      "Lnet/irisshaders/iris/gl/texture/TextureType;apply(IIIIIIILjava/nio/ByteBuffer;)V"),
      allow = 1)
  private void portalix$resizedDimensions(Args args) {
    args.set(1, Math.max(1, args.<Integer>get(1)));
    args.set(2, Math.max(1, args.<Integer>get(2)));
  }
}
