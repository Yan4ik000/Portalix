package dev.portalix.client.mixin.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.portalix.client.portal.entity.PortalEntityRenderStateAccess;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
abstract class PortalItemModelRotationMixin {
  @Inject(
      method =
          "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
          ordinal = 0, shift = At.Shift.AFTER),
      require = 1, allow = 1)
  private void portalix$rotateModel(
      ItemEntityRenderState state, PoseStack poses, SubmitNodeCollector collector,
      CameraRenderState camera, CallbackInfo callback) {
    var rotation = ((PortalEntityRenderStateAccess) state).portalix$modelRotation();
    if (rotation != null) poses.mulPose(rotation);
  }
}
