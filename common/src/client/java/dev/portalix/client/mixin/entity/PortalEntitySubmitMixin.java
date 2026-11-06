package dev.portalix.client.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.portalix.client.portal.entity.PortalEntityScenes;
import dev.portalix.client.portal.entity.PortalEntityRenderStateAccess;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class PortalEntitySubmitMixin {
  @WrapOperation(
      method = "submitEntities",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
  private void portalix$split(
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
          localCamera ->
              original.call(dispatcher, state, localCamera, 0.0, 0.0, 0.0, poses, collector));
  }
}
