package dev.portalix.client.mixin.compat.iris;

import com.mojang.blaze3d.opengl.FrameBufferCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice")
public interface GlDeviceAccessor {
  @Accessor("frameBufferCache")
  FrameBufferCache portalix$framebuffers();
}
