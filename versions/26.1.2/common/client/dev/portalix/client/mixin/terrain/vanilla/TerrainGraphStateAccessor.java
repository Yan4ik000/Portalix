package dev.portalix.client.mixin.terrain.vanilla;

import net.minecraft.client.renderer.SectionOcclusionGraph.GraphStorage;
import net.minecraft.client.renderer.SectionOcclusionGraph.GraphEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.renderer.SectionOcclusionGraph$GraphState")
public interface TerrainGraphStateAccessor {
  @Accessor("storage")
  GraphStorage portalix$storage();

  @Accessor("events")
  GraphEvents portalix$events();
}
