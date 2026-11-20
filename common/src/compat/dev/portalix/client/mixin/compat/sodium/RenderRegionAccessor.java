package dev.portalix.client.mixin.compat.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(value = RenderRegion.class, remap = false)
public interface RenderRegionAccessor {
  @Accessor("sections")
  RenderSection[] portalix$sections();
}
