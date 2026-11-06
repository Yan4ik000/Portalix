package dev.portalix.client.mixin.terrain.vanilla;

import java.util.concurrent.BlockingQueue;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.renderer.SectionOcclusionGraph$GraphState")
public interface TerrainGraphStateAccessor {
  @Accessor("sectionsToPropagateFrom")
  BlockingQueue<RenderSection> portalix$propagation();
}
