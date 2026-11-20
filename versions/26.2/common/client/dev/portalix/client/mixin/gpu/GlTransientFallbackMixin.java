package dev.portalix.client.mixin.gpu;

import com.mojang.blaze3d.util.TransientBlockAllocator;
import dev.portalix.client.render.TransientStagingAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlTransientMemory$Fallback")
abstract class GlTransientFallbackMixin implements TransientStagingAccess {
  @Shadow @Final private TransientBlockAllocator<?> blockAllocator;

  @Override
  public long portalix$stagingBlockSize() {
    return blockAllocator.blockSize();
  }

  @Inject(method = "close", at = @At("TAIL"))
  private void portalix$releasePools(CallbackInfo ci) {
    blockAllocator.close();
    ((GlTransientMemoryAccessor) this).portalix$cpuAllocator().close();
  }
}
