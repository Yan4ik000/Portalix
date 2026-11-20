package dev.portalix.client.mixin.compat.iris;

import dev.portalix.client.compat.iris.IrisPipelineHistory;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.irisshaders.iris.uniforms.CameraUniforms$CameraPositionTracker", remap = false)
abstract class IrisPreviousCameraMixin {
  @Shadow private Vector3d previousCameraPosition;
  @Shadow private Vector3d currentCameraPosition;
  @Shadow private Vector3d previousCameraPositionUnshifted;
  @Shadow private Vector3d currentCameraPositionUnshifted;
  @Unique private Object portalix$historyToken;

  @Inject(method = "update", at = @At("RETURN"))
  private void portalix$seedPreviousCamera(CallbackInfo ci) {
    var token = IrisPipelineHistory.currentToken();
    if (token == portalix$historyToken) return;
    portalix$historyToken = token;
    previousCameraPosition.set(currentCameraPosition);
    previousCameraPositionUnshifted.set(currentCameraPositionUnshifted);
  }
}
