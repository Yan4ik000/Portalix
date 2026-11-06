package dev.portalix.client.mixin.compat.iris;

import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pathways.colorspace.ColorSpace;
import net.irisshaders.iris.pathways.colorspace.ColorSpaceFragmentConverter;
import org.lwjgl.opengl.GL30C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ColorSpaceFragmentConverter.class)
abstract class IrisFragmentColorSpaceResizeMixin {
  @Shadow private int width;
  @Shadow private int height;
  @Shadow private ColorSpace colorSpace;
  @Shadow private Program program;
  @Shadow private int swapTexture;

  @Inject(method = "rebuildProgram", at = @At("HEAD"), cancellable = true)
  private void portalix$resizeStorage(
      int width, int height, ColorSpace colorSpace, CallbackInfo ci) {
    if (program == null || this.colorSpace != colorSpace) return;
    if (this.width != width || this.height != height) {
      IrisRenderSystem.texImage2D(
          swapTexture, GL30C.GL_TEXTURE_2D, 0, GL30C.GL_RGBA8, width, height, 0,
          GL30C.GL_RGBA, GL30C.GL_UNSIGNED_BYTE, null);
      this.width = width;
      this.height = height;
    }
    ci.cancel();
  }
}
