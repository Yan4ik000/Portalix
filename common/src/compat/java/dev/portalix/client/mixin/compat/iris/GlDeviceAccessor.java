package dev.portalix.client.mixin.compat.iris;

import com.mojang.renderpearl.backend.opengl.FrameBufferCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlDevice")
public interface GlDeviceAccessor {
  @Accessor("frameBufferCache")
  FrameBufferCache portalix$framebuffers();
}
