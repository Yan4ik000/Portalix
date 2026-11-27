package dev.portalix.client.mixin.scene;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.portal.PortalMousePicking;
import dev.portalix.client.portal.entity.PortalEntityScenes;
import dev.portalix.client.portal.scene.PortalSceneManager;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class PortalLevelExtractorMixin {
  @WrapOperation(method = "extractVisibleEntities", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z"))
  private boolean portalix$deferParts(EntityRenderDispatcher dispatcher, Entity entity,
      Frustum frustum, double x, double y, double z, Operation<Boolean> original) {
    return !PortalEntityScenes.isManaged(entity) && original.call(dispatcher, entity, frustum, x, y, z);
  }

  @WrapOperation(method = "extractVisibleEntities", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/world/entity/Entity;hasIndirectPassenger(Lnet/minecraft/world/entity/Entity;)Z"))
  private boolean portalix$deferPassengerParts(Entity entity, Entity passenger, Operation<Boolean> original) {
    return !PortalEntityScenes.isManaged(entity) && original.call(entity, passenger);
  }

  @Inject(method = "extractVisibleEntities", at = @At("TAIL"))
  private void portalix$entityParts(Camera camera, Frustum frustum, DeltaTracker delta,
      LevelRenderState output, CallbackInfo ci) {
    PortalEntityScenes.extractParts(frustum, delta, output);
  }

  @Inject(method = "extractBlockOutline", at = @At("HEAD"), cancellable = true)
  private void portalix$outlineSpace(Camera camera, LevelRenderState state, CallbackInfo ci) {
    if (!PortalMousePicking.outlineIn(PortalSceneManager.path())) {
      state.blockOutlineRenderState = null;
      ci.cancel();
    }
  }

  @ModifyArg(method = "extractLevel", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/renderer/LevelRenderer;extractVisibleEntities(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V"))
  private Frustum portalix$entityWindow(Frustum ordinary) {
    return PortalSceneManager.entityFrustum(ordinary);
  }

  @Inject(method = "onResourceManagerReload", at = @At("HEAD"))
  private void portalix$reload(ResourceManager resources, CallbackInfo ci) {
    PortalSceneManager.clear();
  }
}
