package dev.portalix.client.mixin.terrain;

import com.mojang.blaze3d.vertex.StagingBuffer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SectionRenderDispatcher.class)
public interface SectionRenderDispatcherAccessor {
  @Accessor("closed")
  boolean portalix$closed();

  @Accessor("stagingBuffer")
  StagingBuffer portalix$stagingBuffer();
}
