package dev.portalix.client.mixin.compat.iris;

import dev.portalix.client.portal.scene.PortalSceneManager;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CommonUniforms.class)
abstract class IrisEyeBrightnessMixin {
  @Inject(method = "getEyeBrightness", at = @At("HEAD"), cancellable = true)
  private static void portalix$viewLight(CallbackInfoReturnable<Vector2i> cir) {
    var eye = PortalSceneManager.environmentPosition();
    if (eye == null) return;
    var level = Minecraft.getInstance().level;
    var block = BlockPos.containing(eye);
    cir.setReturnValue(
        new Vector2i(
            level.getBrightness(LightLayer.BLOCK, block) * 16,
            level.getBrightness(LightLayer.SKY, block) * 16));
  }
}
