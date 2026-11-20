package dev.portalix.test.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.client.portal.terrain.PortalTerrainViewArea;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.WorldBorderRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class RegressionWorldBorderMixin {
  @Unique private SectionPos portalix$borderSection;

  @WrapOperation(method = "repositionCamera", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/renderer/WorldBorderRenderer;invalidate()V"))
  private void portalix$recordBorderInvalidation(WorldBorderRenderer border, Operation<Void> original,
      @Local(argsOnly = true) CameraRenderState camera) {
    original.call(border);
    portalix$borderSection = SectionPos.of(camera.pos);
  }

  @Inject(method = "repositionCamera", at = @At("RETURN"))
  private void portalix$verifyBorderCamera(CameraRenderState camera, CallbackInfo callback) {
    var renderer = (LevelRenderer) (Object) this;
    if (!PortalSceneManager.isPortalView() && renderer.viewArea() instanceof PortalTerrainViewArea
        && !SectionPos.of(camera.pos).equals(portalix$borderSection))
      throw new AssertionError("World border mesh still belongs to " + portalix$borderSection
          + " after moving the main camera to " + SectionPos.of(camera.pos));
  }
}
