package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.compat.sodium.SodiumPortalFrustum;
import net.caffeinemc.mods.sodium.client.render.chunk.tree.RemovableMultiForest;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(value = RemovableMultiForest.class, remap = false)
abstract class SodiumFallbackTraversalMixin {
  @WrapOperation(method = "traverse", at = @At(value = "INVOKE", target =
      "Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;getTransform()Lnet/caffeinemc/mods/sodium/client/render/viewport/CameraTransform;"))
  private CameraTransform portalix$eyeOrder(Viewport viewport, Operation<CameraTransform> original) {
    return original.call(SodiumPortalFrustum.drawViewport(viewport));
  }
}
