package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.portalix.client.compat.iris.IrisEffectTarget;
import dev.portalix.client.portal.scene.PortalSceneManager;
import net.irisshaders.iris.pathways.HandRenderer;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandRenderer.class)
abstract class IrisPortalHandMixin {
  @Inject(
      method = {"renderSolid", "renderTranslucent"},
      at = @At("HEAD"),
      cancellable = true)
  private void portalix$cameraLocalHand(CallbackInfo ci) {
    if (PortalSceneManager.isPortalView()) ci.cancel();
  }

  @WrapMethod(method = {"renderSolid", "renderTranslucent"})
  private void portalix$handEffects(Matrix4fc modelMatrix, float tickDelta, Camera camera,
      CameraRenderState cameraState, GameRenderer gameRenderer, WorldRenderingPipeline pipeline,
      Operation<Void> original) {
    IrisEffectTarget.handPass(() ->
        original.call(modelMatrix, tickDelta, camera, cameraState, gameRenderer, pipeline));
  }

  @Inject(method = {"renderSolid", "renderTranslucent"}, at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;close()V"))
  private void portalix$drawHandEffects(CallbackInfo ci) {
    IrisEffectTarget.drawHandEffects();
  }
}
