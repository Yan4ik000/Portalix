package dev.portalix.client.mixin.compat.sodium;

import java.util.List;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkSortOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(value = RenderSection.class, remap = false)
public interface RenderSectionAccessor {
  @Accessor("runningJobs")
  List<ChunkJob> portalix$runningJobs();

  @Accessor("pendingBuildOutput")
  ChunkBuildOutput portalix$pendingBuildOutput();

  @Accessor("pendingDynamicSortOutput")
  ChunkSortOutput portalix$pendingSortOutput();
}
