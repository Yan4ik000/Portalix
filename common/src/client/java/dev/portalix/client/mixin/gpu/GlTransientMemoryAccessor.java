package dev.portalix.client.mixin.gpu;

import com.mojang.renderpearl.backend.opengl.GlTransientMemory;
import com.mojang.renderpearl.backend.util.TransientBlockAllocator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GlTransientMemory.class)
public interface GlTransientMemoryAccessor {
  @Accessor("cpuBlockAllocator")
  TransientBlockAllocator<TransientBlockAllocator.Allocator.CpuBlock> portalix$cpuAllocator();
}
