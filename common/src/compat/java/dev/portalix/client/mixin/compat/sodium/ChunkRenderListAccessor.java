package dev.portalix.client.mixin.compat.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(value = ChunkRenderList.class, remap = false)
public interface ChunkRenderListAccessor {
  @Accessor("sectionsWithGeometryMap")
  long[] portalix$geometry();
}
