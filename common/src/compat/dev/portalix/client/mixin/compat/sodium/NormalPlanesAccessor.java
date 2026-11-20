package dev.portalix.client.mixin.compat.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger.NormalPlanes;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(value = NormalPlanes.class, remap = false)
public interface NormalPlanesAccessor {
  @Accessor("normal")
  Vector3fc portalix$normal();

  @Accessor("relativeDistances")
  float[] portalix$distances();
}
