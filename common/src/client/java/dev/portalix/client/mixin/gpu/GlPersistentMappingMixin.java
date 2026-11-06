package dev.portalix.client.mixin.gpu;

import com.mojang.blaze3d.util.TransientBlockAllocator;
import dev.portalix.client.render.TransientStagingAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlTransientMemory$PersistentMapping")
abstract class GlPersistentMappingMixin implements TransientStagingAccess {
  @Shadow @Final private TransientBlockAllocator<?> stagingBlockAllocator;
  @Shadow @Final private TransientBlockAllocator<?> gpuBlockAllocator;
  @Shadow @Final private TransientBlockAllocator<?> gpuMappedBlockAllocator;

  @Override
  public long portalix$stagingBlockSize() {
    return stagingBlockAllocator.blockSize();
  }

  @Inject(method = "close", at = @At("TAIL"))
  private void portalix$releasePools(CallbackInfo ci) {

    stagingBlockAllocator.close();
    gpuBlockAllocator.close();
    gpuMappedBlockAllocator.close();
    ((GlTransientMemoryAccessor) this).portalix$cpuAllocator().close();
  }
}
