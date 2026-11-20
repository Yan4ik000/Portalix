package dev.portalix.client.mixin.compat.iris;

import net.irisshaders.iris.gl.program.ComputeProgram;
import net.irisshaders.iris.pathways.colorspace.ColorSpace;
import net.irisshaders.iris.pathways.colorspace.ColorSpaceComputeConverter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ColorSpaceComputeConverter.class)
abstract class IrisComputeColorSpaceResizeMixin {
  @Shadow private int width;
  @Shadow private int height;
  @Shadow private ColorSpace colorSpace;
  @Shadow private ComputeProgram program;

  @Inject(method = "rebuildProgram", at = @At("HEAD"), cancellable = true)
  private void portalix$resizeDispatch(
      int width, int height, ColorSpace colorSpace, CallbackInfo ci) {
    if (program == null || this.colorSpace != colorSpace) return;
    // Dimensions affect dispatch, while only the color space specializes the shader.
    this.width = width;
    this.height = height;
    ci.cancel();
  }
}
