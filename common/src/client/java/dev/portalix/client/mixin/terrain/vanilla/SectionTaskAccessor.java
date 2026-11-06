package dev.portalix.client.mixin.terrain.vanilla;

import java.util.concurrent.atomic.AtomicBoolean;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(
    targets =
        "net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection$SectionTask")
public interface SectionTaskAccessor {
  @Accessor("isCancelled")
  AtomicBoolean portalix$cancelled();
}
