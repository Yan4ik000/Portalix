package dev.portalix.client.mixin.compat.sodium;

import dev.portalix.client.compat.sodium.SodiumTopoQuadsAccess;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.DynamicTopoData;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.quad.TQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(value = DynamicTopoData.class, remap = false)
public interface DynamicTopoDataAccessor extends SodiumTopoQuadsAccess {
  @Override
  @Accessor("quads")
  TQuad[] portalix$quads();
}
