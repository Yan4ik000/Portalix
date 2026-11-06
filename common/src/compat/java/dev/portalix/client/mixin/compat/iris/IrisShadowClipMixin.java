package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.portalix.client.portal.clip.PortalClipping;
import net.irisshaders.iris.mixin.LevelRendererAccessor;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ShadowRenderer.class)
abstract class IrisShadowClipMixin {
  @WrapMethod(method = "renderShadows")
  private void portalix$unclippedShadows(
      LevelRendererAccessor renderer,
      Camera camera,
      CameraRenderState state,
      Operation<Void> original) {
    PortalClipping.without(() -> original.call(renderer, camera, state));
  }
}
