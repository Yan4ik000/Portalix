package dev.portalix.client.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.client.portal.clip.PortalFrustumState;
import dev.portalix.client.portal.entity.PortalEntityScenes;
import dev.portalix.portal.traversal.PortalPassagePose;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
abstract class PortalEntityCullingMixin {
  @Shadow protected abstract AABB getBoundingBoxForCulling(Entity entity, float partialTick);

  @WrapOperation(method = "shouldRender", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/renderer/entity/EntityRenderer;affectedByCulling(Lnet/minecraft/world/entity/Entity;)Z"))
  private boolean portalix$limitUnculledEntities(EntityRenderer<?, ?> renderer, Entity entity,
      Operation<Boolean> original, @Local(argsOnly = true) Frustum frustum,
      @Local(argsOnly = true) float partialTick) {
    if (original.call(renderer, entity)) return true;
    var window = ((PortalFrustumState) frustum).portalix$terrainWindow();
    if (window == null) return false;
    var box = getBoundingBoxForCulling(entity, partialTick);
    return window.classifyBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)
        == org.joml.FrustumIntersection.OUTSIDE;
  }

  @Inject(method = "getBoundingBoxForCulling", at = @At("RETURN"), cancellable = true)
  private void portalix$bodyBounds(Entity entity, float partialTick, CallbackInfoReturnable<AABB> callback) {
    if (PortalPassagePose.compact(entity))
      callback.setReturnValue(
          callback.getReturnValue().minmax(PortalPassagePose.interactionBounds(entity)));
  }

  @Inject(method = "affectedByCulling", at = @At("RETURN"), cancellable = true)
  private void portalix$portalVisibility(Entity entity, CallbackInfoReturnable<Boolean> callback) {
    if (PortalEntityScenes.visibleThroughPortal(entity)) callback.setReturnValue(false);
  }
}
