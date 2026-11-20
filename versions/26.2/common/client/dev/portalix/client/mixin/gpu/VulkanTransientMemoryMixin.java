package dev.portalix.client.mixin.gpu;

import com.mojang.blaze3d.util.TransientBlockAllocator;
import dev.portalix.client.render.TransientStagingAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "com.mojang.blaze3d.vulkan.VulkanTransientMemory")
abstract class VulkanTransientMemoryMixin implements TransientStagingAccess {
  @Shadow @Final private TransientBlockAllocator<?> stagingBlockAllocator;

  @Override
  public long portalix$stagingBlockSize() {
    return stagingBlockAllocator.blockSize();
  }
}
