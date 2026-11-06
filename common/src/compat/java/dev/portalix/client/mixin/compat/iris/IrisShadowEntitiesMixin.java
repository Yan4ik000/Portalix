package dev.portalix.client.mixin.compat.iris;

import com.google.common.collect.Iterables;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.portalix.client.portal.entity.PortalEntityScenes;
import dev.portalix.client.portal.entity.PortalEntityRenderStateAccess;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShadowRenderer.class)
abstract class IrisShadowEntitiesMixin {
  @ModifyExpressionValue(
      method = "extractVisibleEntities",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/multiplayer/ClientLevel;entitiesForRendering()Ljava/lang/Iterable;"))
  private Iterable<Entity> portalix$ordinaryEntities(Iterable<Entity> entities) {
    return Iterables.filter(entities, entity -> !PortalEntityScenes.isManaged(entity));
  }

  @Inject(method = "extractVisibleEntities", at = @At("TAIL"))
  private void portalix$shadowParts(
      Camera camera,
      Frustum frustum,
      DeltaTracker delta,
      LevelRenderState output,
      CallbackInfo ci) {
    PortalEntityScenes.extractParts(frustum, delta, output);
  }

  @WrapOperation(
      method = "renderEntities",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
  private void portalix$submitParts(
      EntityRenderDispatcher dispatcher,
      EntityRenderState state,
      CameraRenderState camera,
      double x,
      double y,
      double z,
      PoseStack poses,
      SubmitNodeCollector collector,
      Operation<Void> original) {
    if (((PortalEntityRenderStateAccess) state).portalix$slices() == null)
      original.call(dispatcher, state, camera, x, y, z, poses, collector);
    else
      PortalEntityScenes.submit(
          state,
          camera,
          poses,
          collector,
          local -> original.call(dispatcher, state, local, 0.0, 0.0, 0.0, poses, collector));
  }
}
