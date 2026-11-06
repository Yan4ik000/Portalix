package dev.portalix.client.mixin.entity;

import dev.portalix.client.portal.entity.PortalEntityScenes;
import dev.portalix.portal.traversal.PortalPassagePose;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
abstract class PortalEntityCullingMixin {
  @Inject(method = "getBoundingBoxForCulling", at = @At("RETURN"), cancellable = true)
  private void portalix$bodyBounds(Entity entity, CallbackInfoReturnable<AABB> callback) {
    if (PortalPassagePose.compact(entity))
      callback.setReturnValue(
          callback.getReturnValue().minmax(PortalPassagePose.interactionBounds(entity)));
  }

  @Inject(method = "affectedByCulling", at = @At("RETURN"), cancellable = true)
  private void portalix$portalVisibility(Entity entity, CallbackInfoReturnable<Boolean> callback) {
    if (PortalEntityScenes.visibleThroughPortal(entity)) callback.setReturnValue(false);
  }
}
