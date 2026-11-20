package dev.portalix.client.mixin.gpu;

import com.mojang.renderpearl.backend.opengl.DirectStateAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlCommandEncoder")
abstract class GlTextureCopyMixin {
  @Redirect(
      method = "copyTextureToTexture",
      at = @At(
          value = "INVOKE",
          target = "Lcom/mojang/renderpearl/backend/opengl/DirectStateAccess;blitFrameBuffers(IIIIIIIIIIII)V"),
      require = 1,
      allow = 1)
  private void portalix$copyTextureRegion(
      DirectStateAccess gl, int source, int destination,
      int sourceX, int sourceY, int width, int height,
      int destinationX, int destinationY, int destinationWidth, int destinationHeight,
      int mask, int filter) {
    // The encoder accepts an extent, while a framebuffer blit takes two corners.
    gl.blitFrameBuffers(
        source, destination, sourceX, sourceY, sourceX + width, sourceY + height,
        destinationX, destinationY, destinationX + destinationWidth, destinationY + destinationHeight,
        mask, filter);
  }
}
