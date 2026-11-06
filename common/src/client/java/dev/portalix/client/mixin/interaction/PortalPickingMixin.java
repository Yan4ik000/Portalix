package dev.portalix.client.mixin.interaction;

import dev.portalix.client.portal.PortalMousePicking;
import dev.portalix.client.portal.camera.PortalCameraController;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
abstract class PortalPickingMixin {
  @Inject(method = "raycastHitResult", at = @At("HEAD"), cancellable = true)
  private void portalix$pick(
      float partial, Entity cameraEntity, CallbackInfoReturnable<HitResult> callback) {
    var player = (LocalPlayer) (Object) this;
    PortalMousePicking.select(null);
    if (cameraEntity != player) return;
    var pose = PortalCameraController.raycast(partial);
    if (pose == null) return;
    var ray = PortalMousePicking.pick(player, pose, player.blockInteractionRange(),
        player.entityInteractionRange(), player.getActiveItem().get(DataComponents.ATTACK_RANGE));
    if (ray == null) return;
    if (ray.hit().getType() != HitResult.Type.MISS) PortalMousePicking.select(ray, pose.spacePath());
    callback.setReturnValue(ray.hit());
  }
}
