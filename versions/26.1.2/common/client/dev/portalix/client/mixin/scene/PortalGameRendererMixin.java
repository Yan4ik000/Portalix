package dev.portalix.client.mixin.scene;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.portalix.client.PortalixClient;
import dev.portalix.client.mixin.entity.EntityRendererAccessor;
import dev.portalix.client.portal.camera.PortalCameraController;
import dev.portalix.client.portal.clip.PortalClipping;
import dev.portalix.client.portal.entity.PortalEntityScenes;
import dev.portalix.client.portal.render.PortalSurfaceRenderer;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.client.portal.scene.PortalViewHorizon;
import dev.portalix.client.portal.terrain.PortalFrameTerrainBudget;
import dev.portalix.portal.traversal.PortalEntityCollisions;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.LevelRenderer;
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
  @Inject(method = "update", at = @At("HEAD"))
  private void portalix$beginFrame(DeltaTracker delta, boolean advance, CallbackInfo ci) {
    PortalFrameTerrainBudget.beginFrame();
    if (Minecraft.getInstance().level != null) PortalixClient.carryController().beginRenderFrame();
  }

  @WrapOperation(method = "extractCamera", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/renderer/fog/FogRenderer;setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/multiplayer/ClientLevel;)Lnet/minecraft/client/renderer/fog/FogData;"))
  private FogData portalix$cameraFog(FogRenderer renderer, Camera camera, int distance, DeltaTracker delta,
      float darkening, ClientLevel level, Operation<FogData> original) {
    PortalViewHorizon.beginFrame(delta);
    double radius = PortalSceneManager.fogRadius(distance);
    var fog = original.call(renderer, camera, (int) Math.ceil(radius), delta, darkening, level);
    PortalSceneManager.positionTerrainFog(fog, camera.position(), radius);
    return fog;
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

  @WrapOperation(method = "update", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/renderer/LevelRenderer;update(Lnet/minecraft/client/Camera;)V"))
  private void portalix$deferTerrain(LevelRenderer renderer, Camera camera, Operation<Void> original) {
    // The terrain window and its frame budget are selected together with the extracted camera.
  }

  @WrapOperation(
      method = "extract",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/LevelRenderer;extractLevel(Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/Camera;F)V"))
  private void portalix$extract(
      LevelRenderer renderer,
      DeltaTracker delta,
      Camera camera,
      float partialTick,
      Operation<Void> original) {
    GameRenderer self = (GameRenderer) (Object) this;
    PortalSceneManager.extract(
        delta, camera, self.getGameRenderState().levelRenderState.cameraRenderState);
    renderer.update(camera);
    original.call(renderer, delta, camera, partialTick);
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

  @Inject(method = "getMainCamera", at = @At("HEAD"), cancellable = true)
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
    if (Minecraft.getInstance().level != null) PortalixClient.shotEffects().endFrame();
    PortalEntityScenes.endFrame();
    PortalClipping.endFrame();
    PortalSurfaceRenderer.endFrame();
  }

  @Inject(method = "close", at = @At("TAIL"))
  private void portalix$clipClose(CallbackInfo ci) {
    PortalClipping.close();
  }
}
