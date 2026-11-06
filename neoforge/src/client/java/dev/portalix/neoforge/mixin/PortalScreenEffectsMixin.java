package dev.portalix.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.portal.camera.PortalCameraEnvironment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ScreenEffectRenderer.class)
abstract class PortalScreenEffectsMixin {
  @Inject(method = "getViewBlockingStateAndPos", at = @At("HEAD"), cancellable = true)
  private static void portalix$blockingBlock(
      Player player, CallbackInfoReturnable<Pair<BlockState, BlockPos>> cir) {
    if (PortalCameraEnvironment.applies(player)) {
      var block = PortalCameraEnvironment.blockingBlock(player);
      cir.setReturnValue(block == null ? null : Pair.of(block.state(), block.position()));
    }
  }

  @WrapOperation(
      method = "submit",
      at = @At(
          value = "INVOKE",
          target = "Lnet/minecraft/world/entity/EntityFluidInteraction;getFirstEyeInFluid()Lnet/neoforged/neoforge/fluids/FluidType;"))
  private FluidType portalix$cameraFluid(
      EntityFluidInteraction interaction, Operation<FluidType> original) {
    var client = Minecraft.getInstance();
    if (!PortalCameraEnvironment.applies(client.player)) return original.call(interaction);
    var eye = client.gameRenderer.mainCamera().position();
    var block = BlockPos.containing(eye);
    var fluid = client.level.getFluidState(block);
    return !fluid.isEmpty() && eye.y < block.getY() + fluid.getHeight(client.level, block)
        ? fluid.getFluidType()
        : NeoForgeMod.EMPTY_TYPE.value();
  }
}
