package dev.portalix.client.mixin.camera;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.portal.camera.PortalCameraEnvironment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelExtractor.class)
abstract class PortalScreenEffectsMixin {
  @Inject(method = "getViewBlockingState", at = @At("HEAD"), cancellable = true)
  private static void portalix$blockingState(
      LocalPlayer player, Frustum frustum, CallbackInfoReturnable<BlockState> cir) {
    if (PortalCameraEnvironment.applies(player)) {
      var block = PortalCameraEnvironment.blockingBlock(player, frustum.getNearPlaneBounds());
      cir.setReturnValue(block == null ? null : block.state());
    }
  }

  @WrapOperation(
      method = "extractPlayerState",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/player/LocalPlayer;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
  private boolean portalix$eyeFluid(
      LocalPlayer player, TagKey<Fluid> tag, Operation<Boolean> original) {
    return PortalCameraEnvironment.applies(player)
        ? PortalCameraEnvironment.eyeInFluid(player, tag)
        : original.call(player, tag);
  }

  @WrapOperation(
      method = "extractPlayerState",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/player/LocalPlayer;getEyePosition()Lnet/minecraft/world/phys/Vec3;"),
      require = 1)
  private Vec3 portalix$fluidLight(LocalPlayer player, Operation<Vec3> original) {
    return PortalCameraEnvironment.applies(player)
        ? Minecraft.getInstance().gameRenderer.mainCamera().position()
        : original.call(player);
  }

  @WrapOperation(
      method = "extractPlayerState",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"),
      allow = 1)
  private float portalix$fluidYaw(LocalPlayer player, Operation<Float> original) {
    return PortalCameraEnvironment.applies(player)
        ? Minecraft.getInstance().gameRenderer.mainCamera().yRot()
        : original.call(player);
  }

  @WrapOperation(
      method = "extractPlayerState",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"),
      allow = 1)
  private float portalix$fluidPitch(LocalPlayer player, Operation<Float> original) {
    return PortalCameraEnvironment.applies(player)
        ? Minecraft.getInstance().gameRenderer.mainCamera().xRot()
        : original.call(player);
  }
}
