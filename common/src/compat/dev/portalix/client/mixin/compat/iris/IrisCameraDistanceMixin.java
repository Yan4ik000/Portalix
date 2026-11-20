package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.portalix.client.portal.scene.PortalSceneManager;
import net.irisshaders.iris.uniforms.CameraUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CameraUniforms.class, remap = false)
abstract class IrisCameraDistanceMixin {
  @ModifyReturnValue(method = "getRenderDistanceInBlocks", at = @At("RETURN"))
  private static int portalix$viewDistance(int nativeDistance) {
    var fog = PortalSceneManager.viewFog();
    return fog == null ? nativeDistance : Math.round(fog.renderDistanceEnd);
  }
}
