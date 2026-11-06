package dev.portalix.client.mixin.compat.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.lists.VisibleChunkCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(value = VisibleChunkCollector.class, remap = false)
public interface VisibleChunkCollectorAccessor {
  @Accessor("regions")
  RenderRegionManager portalix$regions();
}
