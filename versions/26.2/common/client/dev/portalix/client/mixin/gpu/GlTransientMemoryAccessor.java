package dev.portalix.client.mixin.gpu;

import com.mojang.blaze3d.opengl.GlTransientMemory;
import com.mojang.blaze3d.util.TransientBlockAllocator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GlTransientMemory.class)
public interface GlTransientMemoryAccessor {
  @Accessor("cpuBlockAllocator")
  TransientBlockAllocator<Long> portalix$cpuAllocator();
}
