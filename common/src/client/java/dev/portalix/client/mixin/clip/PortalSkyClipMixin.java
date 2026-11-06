package dev.portalix.client.mixin.clip;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.framegraph.FramePass;
import dev.portalix.client.portal.clip.PortalClipping;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class PortalSkyClipMixin {
  @WrapOperation(
      method = "addSkyPass*",
      at =
          @At(
              value = "INVOKE",
              target = "Lcom/mojang/blaze3d/framegraph/FramePass;executes(Ljava/lang/Runnable;)V"))
  private void portalix$skyClip(FramePass pass, Runnable render, Operation<Void> original) {
    original.call(pass, (Runnable) () -> PortalClipping.without(render));
  }
}
