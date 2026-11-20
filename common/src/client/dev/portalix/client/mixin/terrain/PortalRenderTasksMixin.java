package dev.portalix.client.mixin.terrain;

import dev.portalix.client.portal.terrain.SharedOcclusionExecutor;
import dev.portalix.client.portal.terrain.SharedTerrainExecutor;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class PortalRenderTasksMixin {
  @Inject(
      method = "close",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;shutdownExecutors()V"))
  private void portalix$closeWorkers(CallbackInfo ci) {
    SharedOcclusionExecutor.close();
    SharedTerrainExecutor.close();
  }
}
