package dev.portalix.client.mixin.compat.sodium;

import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.render.viewport.frustum.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(value = Viewport.class, remap = false)
public interface ViewportAccessor {
  @Accessor("frustum")
  Frustum portalix$frustum();
}
