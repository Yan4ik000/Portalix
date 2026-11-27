package dev.portalix.test.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.client.mixin.scene.LevelRendererAccessor;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.client.portal.terrain.PortalTerrainViewArea;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.WorldBorderRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class RegressionWorldBorderMixin {
  @Unique private SectionPos portalix$borderSection;

  @WrapOperation(method = "cullTerrain", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/renderer/WorldBorderRenderer;invalidate()V"))
  private void portalix$recordBorderInvalidation(WorldBorderRenderer border, Operation<Void> original,
      @Local(argsOnly = true) Camera camera) {
    original.call(border);
    portalix$borderSection = SectionPos.of(camera.position());
  }

  @Inject(method = "cullTerrain", at = @At("RETURN"))
  private void portalix$verifyBorderCamera(Camera camera, Frustum frustum, boolean spectator,
      CallbackInfo callback) {
    var renderer = (LevelRendererAccessor) this;
    var section = SectionPos.of(camera.position());
    if (!PortalSceneManager.isPortalView() && renderer.portalix$viewArea() instanceof PortalTerrainViewArea
        && !section.equals(portalix$borderSection))
      throw new AssertionError("World border mesh still belongs to " + portalix$borderSection
          + " after moving the main camera to " + section);
  }
}
