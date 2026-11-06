package dev.portalix.client.mixin.scene;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.portalix.client.mixin.entity.EntityRendererAccessor;
import dev.portalix.client.portal.camera.PortalCameraController;
import dev.portalix.client.portal.clip.PortalClipping;
import dev.portalix.client.portal.entity.PortalEntityScenes;
import dev.portalix.client.portal.render.PortalSurfaceRenderer;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.client.portal.terrain.PortalFrameTerrainBudget;
import dev.portalix.portal.traversal.PortalEntityCollisions;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class PortalGameRendererMixin {
  @Inject(method = "extract", at = @At("HEAD"))
  private void portalix$beginFrame(DeltaTracker delta, boolean advance, CallbackInfo ci) {
    PortalFrameTerrainBudget.beginFrame();
  }

  @WrapMethod(method = "renderItemInHand")
  private void portalix$cameraLocalHand(
      CameraRenderState camera, float partial, Matrix4fc rotation, Operation<Void> original) {
    PortalClipping.without(() -> original.call(camera, partial, rotation));
  }

  @WrapOperation(
      method = "renderItemInHand",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;getPackedLightCoords(Lnet/minecraft/world/entity/Entity;F)I"))
  private int portalix$handLight(
      EntityRenderDispatcher dispatcher,
      Entity entity,
      float partial,
      Operation<Integer> original) {
    if (!PortalCameraController.active() && PortalEntityCollisions.intersectingPortal(entity) == null)
      return original.call(dispatcher, entity, partial);
    var view = PortalCameraController.currentView();
    if (view == null) return original.call(dispatcher, entity, partial);
    var renderer = (EntityRendererAccessor) dispatcher.getRenderer(entity);
    var probe = BlockPos.containing(view.position());
    return LightCoordsUtil.pack(
        renderer.portalix$blockLight(entity, probe), renderer.portalix$skyLight(entity, probe));
  }

  @ModifyExpressionValue(
      method = "renderLevel",
      at =
          @At(
              value = "INVOKE",
              target = "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;pose()Lorg/joml/Matrix4f;"),
      allow = 1)
  private Matrix4f portalix$cameraBob(Matrix4f bob) {
    return PortalCameraController.currentView() == null
        ? bob
        : new Matrix4f(bob).setTranslation(0, 0, 0);
  }

  @WrapOperation(
      method = "extract",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/extract/LevelExtractor;extract(Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/Camera;F)V"))
  private void portalix$extract(
      LevelExtractor extractor,
      DeltaTracker delta,
      Camera camera,
      float partialTick,
      Operation<Void> original) {
    GameRenderer self = (GameRenderer) (Object) this;
    PortalSceneManager.extract(
        delta, camera, self.gameRenderState().levelRenderState.cameraRenderState);
    original.call(extractor, delta, camera, partialTick);
  }

  @WrapOperation(
      method = "renderLevel",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"),
      allow = 1)
  private GpuBufferSlice portalix$projection(
      ProjectionMatrixBuffer buffer,
      Matrix4f matrix,
      Operation<GpuBufferSlice> original) {
    PortalSceneManager.renderProjection(matrix);
    return original.call(buffer, matrix);
  }

  @Inject(method = "mainRenderTarget", at = @At("HEAD"), cancellable = true)
  private void portalix$target(CallbackInfoReturnable<RenderTarget> cir) {
    RenderTarget target = PortalSceneManager.target();
    if (target != null) cir.setReturnValue(target);
  }

  @Inject(method = "mainCamera", at = @At("HEAD"), cancellable = true)
  private void portalix$camera(CallbackInfoReturnable<Camera> cir) {
    Camera camera = PortalSceneManager.camera();
    if (camera != null) cir.setReturnValue(camera);
  }

  @Inject(
      method = {"lightmap", "levelLightmap"},
      at = @At("HEAD"),
      cancellable = true)
  private void portalix$lightmap(CallbackInfoReturnable<GpuTextureView> cir) {
    GpuTextureView lightmap = PortalSceneManager.lightmap();
    if (lightmap != null) cir.setReturnValue(lightmap);
  }

  @Inject(
      method = {"resetData", "close"},
      at = @At("HEAD"))
  private void portalix$clear(CallbackInfo ci) {
    PortalSceneManager.clear();
  }

  @Inject(method = "render", at = @At("TAIL"))
  private void portalix$clipFrame(DeltaTracker delta, boolean advance, CallbackInfo ci) {
    PortalEntityScenes.endFrame();
    PortalClipping.endFrame();
    PortalSurfaceRenderer.endFrame();
  }

  @Inject(method = "close", at = @At("TAIL"))
  private void portalix$clipClose(CallbackInfo ci) {
    PortalClipping.close();
  }
}
