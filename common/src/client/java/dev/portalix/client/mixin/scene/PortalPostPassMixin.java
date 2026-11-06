package dev.portalix.client.mixin.scene;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import dev.portalix.client.portal.scene.PortalSceneManager;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PostPass.class)
abstract class PortalPostPassMixin {
  @Shadow @Final private String name;

  @ModifyReceiver(
      method = "*",
      at = {
        @At(
            value = "INVOKE",
            target =
                "Lnet/minecraft/client/renderer/MappableRingBuffer;currentBuffer()Lcom/mojang/blaze3d/buffers/GpuBuffer;"),
        @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MappableRingBuffer;rotate()V")
      },
      require = 3,
      allow = 3)
  private MappableRingBuffer portalix$samplerInfo(MappableRingBuffer buffer) {
    return PortalSceneManager.postProcessBuffer((PostPass) (Object) this, name, buffer);
  }
}
