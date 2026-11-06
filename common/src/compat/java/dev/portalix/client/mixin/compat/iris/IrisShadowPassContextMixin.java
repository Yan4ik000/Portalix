package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import net.irisshaders.iris.apiimpl.IrisApiV0Impl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = IrisApiV0Impl.class, remap = false)
abstract class IrisShadowPassContextMixin {
  @WrapOperation(method = "isRenderingShadowPass()Z",
      at = @At(value = "INVOKE", target =
          "Lnet/irisshaders/iris/shadows/ShadowRenderingState;areShadowsCurrentlyBeingRendered()Z"),
      require = 1, allow = 1)
  private static boolean portalix$renderThreadShadowPass(Operation<Boolean> original) {
    // Native server constructors also query ItemStack.hasFoil while the client renders shadows.
    return RenderSystem.isOnRenderThread() && original.call();
  }
}
