package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.irisshaders.iris.gl.texture.TextureScaleOverride;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TextureScaleOverride.class)
abstract class IrisTextureScaleMixin {
  @Shadow @Final public boolean isXRelative;
  @Shadow @Final public boolean isYRelative;

  @ModifyReturnValue(method = "getX", at = @At("RETURN"), allow = 2)
  private int portalix$minimumWidth(int width) {
    return isXRelative ? Math.max(1, width) : width;
  }

  @ModifyReturnValue(method = "getY", at = @At("RETURN"), allow = 2)
  private int portalix$minimumHeight(int height) {
    return isYRelative ? Math.max(1, height) : height;
  }
}
