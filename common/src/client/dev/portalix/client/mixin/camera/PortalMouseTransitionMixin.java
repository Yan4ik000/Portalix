package dev.portalix.client.mixin.camera;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.portal.camera.PortalCameraController;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MouseHandler.class)
abstract class PortalMouseTransitionMixin {
  @WrapOperation(
      method = "turnPlayer",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
  private void portalix$rolledMouse(
      LocalPlayer player, double horizontal, double vertical, Operation<Void> original) {
    var angles = PortalCameraController.mouse(player, horizontal, vertical);
    if (angles == null) {
      original.call(player, horizontal, vertical);
    } else {
      double yaw = Mth.wrapDegrees(angles.yaw() - player.getYRot()) / .15;
      double pitch = (angles.pitch() - player.getXRot()) / .15;
      float expectedYaw = player.getYRot() + (float) yaw * .15F;
      float expectedPitch = Mth.clamp(player.getXRot() + (float) pitch * .15F, -90, 90);
      original.call(player, yaw, pitch);
      PortalCameraController.turned(player, expectedYaw, expectedPitch);
    }
  }
}
