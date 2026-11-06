package dev.portalix.client.mixin.scene;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.compat.PortalTerrainBackend;
import dev.portalix.client.compat.minecraft.SceneExtractorAccess;
import dev.portalix.client.portal.PortalMousePicking;
import dev.portalix.client.portal.entity.PortalEntityScenes;
import dev.portalix.client.portal.scene.PortalSceneManager;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.SectionUpdateTracker;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.SectionPos;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelExtractor.class)
public abstract class PortalLevelExtractorMixin {
  @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
  private void portalix$deferParts(
      Entity entity,
      Frustum frustum,
      double x,
      double y,
      double z,
      CallbackInfoReturnable<Boolean> cir) {
    if (PortalEntityScenes.isManaged(entity)) cir.setReturnValue(false);
  }

  @Inject(method = "extractVisibleEntities", at = @At("TAIL"))
  private void portalix$entityParts(
      Camera camera,
      Frustum frustum,
      DeltaTracker delta,
      LevelRenderState output,
      CallbackInfo ci) {
    PortalEntityScenes.extractParts(frustum, delta, output);
  }

  @Inject(method = "extractBlockOutline", at = @At("HEAD"), cancellable = true)
  private void portalix$outlineSpace(Camera camera, LevelRenderState state, CallbackInfo ci) {
    if (!PortalMousePicking.outlineIn(PortalSceneManager.path())) {
      state.blockOutlineRenderState = null;
      ci.cancel();
    }
  }

  @Shadow @Final private LevelRenderState levelRenderState;
  @Unique private float portalix$previousRoll;

  @WrapOperation(
      method = "allChanged",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/multiplayer/ClientLevel;clearTintCaches()V"))
  private void portalix$sharedTintCaches(ClientLevel level, Operation<Void> original) {

    if (!PortalSceneManager.isPortalView()) original.call(level);
  }

  @ModifyArg(
      method = "extract",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/extract/LevelExtractor;extractVisibleEntities(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V"))
  private Frustum portalix$entityWindow(Frustum ordinary) {
    return PortalSceneManager.entityFrustum(ordinary);
  }

  @Inject(method = "extract", at = @At("HEAD"))
  private void portalix$rollFrustum(
      DeltaTracker delta, Camera camera, float partial, CallbackInfo ci) {
    Quaternionf upright =
        new Quaternionf()
            .rotationYXZ(
                (float) Math.PI - (float) Math.toRadians(camera.yRot()),
                -(float) Math.toRadians(camera.xRot()),
                0);
    Vector3f up = camera.rotation().transform(new Vector3f(0, 1, 0));
    Vector3f reference = upright.transform(new Vector3f(0, 1, 0));
    float roll =
        (float)
            Math.atan2(
                new Vector3f(reference).cross(up).dot(camera.forwardVector()), reference.dot(up));
    if (Math.abs(roll - portalix$previousRoll) > 1.0e-5F)
      ((SceneExtractorAccess) this).portalix$invalidateView();
    portalix$previousRoll = roll;
  }

  @WrapOperation(
      method = "extract",
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/client/renderer/SectionOcclusionGraph;consumeFrustumUpdate()Z"),
      allow = 1)
  private boolean portalix$viewInvalidation(
      SectionOcclusionGraph graph, Operation<Boolean> original) {
    boolean changed = original.call(graph);
    boolean invalid = ((SceneExtractorAccess) this).portalix$consumeViewInvalidation();
    return changed || invalid;
  }

  @WrapOperation(
      method = "extract",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/multiplayer/ClientChunkCache;flipUpdateTrackingSets()V"))
  private void portalix$retainChunkEvents(ClientChunkCache cache, Operation<Void> original) {
    if (!PortalSceneManager.isPortalView()) original.call(cache);
    else if (PortalTerrainBackend.usesVanillaChunkEvents())
      PortalSceneManager.copyChunkEvents(levelRenderState.chunkLoadingRenderState);
  }

  @WrapOperation(
      method = "extract",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/SectionUpdateTracker;repositionCamera(Lnet/minecraft/core/SectionPos;)V"))
  private void portalix$primaryTracker(
      SectionUpdateTracker tracker, SectionPos pos, Operation<Void> original) {
    if (!PortalSceneManager.isPortalView()) original.call(tracker, pos);
  }

  @Inject(method = "onResourceManagerReload", at = @At("HEAD"))
  private void portalix$reload(ResourceManager resources, CallbackInfo ci) {
    PortalSceneManager.clear();
  }
}
