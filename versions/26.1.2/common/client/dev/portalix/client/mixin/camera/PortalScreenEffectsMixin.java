package dev.portalix.client.mixin.camera;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.client.compat.minecraft.MinecraftRenderAccess;
import dev.portalix.client.portal.camera.PortalCameraEnvironment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ScreenEffectRenderer.class)
abstract class PortalScreenEffectsMixin {
  @Inject(method = "getViewBlockingState", at = @At("HEAD"), cancellable = true)
  private static void portalix$blockingState(
      Player player, CallbackInfoReturnable<BlockState> cir) {
    if (PortalCameraEnvironment.applies(player)) {
      var block = PortalCameraEnvironment.blockingBlock(player);
      cir.setReturnValue(block == null ? null : block.state());
    }
  }

  @WrapOperation(
      method = "renderScreenEffect",
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
      method = {"renderWater", "renderFluid"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/core/BlockPos;containing(DDD)Lnet/minecraft/core/BlockPos;"),
      require = 1, allow = 1)
  private static BlockPos portalix$fluidLight(double x, double y, double z,
      Operation<BlockPos> original, @Local(argsOnly = true) Minecraft client) {
    if (!PortalCameraEnvironment.applies(client.player)) return original.call(x, y, z);
    return BlockPos.containing(MinecraftRenderAccess.camera(client).position());
  }

  @WrapOperation(
      method = {"renderWater", "renderFluid"},
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"),
      require = 1, allow = 1)
  private static float portalix$fluidYaw(LocalPlayer player, Operation<Float> original,
      @Local(argsOnly = true) Minecraft client) {
    return PortalCameraEnvironment.applies(player)
        ? MinecraftRenderAccess.camera(client).yRot()
        : original.call(player);
  }

  @WrapOperation(
      method = {"renderWater", "renderFluid"},
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"),
      require = 1, allow = 1)
  private static float portalix$fluidPitch(LocalPlayer player, Operation<Float> original,
      @Local(argsOnly = true) Minecraft client) {
    return PortalCameraEnvironment.applies(player)
        ? MinecraftRenderAccess.camera(client).xRot()
        : original.call(player);
  }
}
