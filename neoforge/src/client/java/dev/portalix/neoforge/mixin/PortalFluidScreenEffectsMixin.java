package dev.portalix.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.client.portal.camera.PortalCameraEnvironment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.PlayerRenderState.WaterOverlay;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(IClientFluidTypeExtensions.class)
interface PortalFluidScreenEffectsMixin {
  @WrapOperation(method = "extractOverlay", at = @At(value = "NEW", target =
      "(IFF)Lnet/minecraft/client/renderer/state/level/PlayerRenderState$WaterOverlay;"))
  private WaterOverlay portalix$fluidRotation(int color, float xOffset, float yOffset,
      Operation<WaterOverlay> original, @Local(argsOnly = true) LocalPlayer player) {
    if (!PortalCameraEnvironment.applies(player)) return original.call(color, xOffset, yOffset);
    var camera = Minecraft.getInstance().gameRenderer.mainCamera();
    return original.call(color, -camera.yRot() / 64.0F, camera.xRot() / 64.0F);
  }
}
