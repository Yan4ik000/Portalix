package dev.portalix.client.mixin.compat.sodium;

import com.mojang.blaze3d.buffers.GpuBuffer;
import net.caffeinemc.mods.sodium.client.gpu.arena.ArenaAggregator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(value = ArenaAggregator.class, remap = false)
public interface ArenaAggregatorAccessor {
  @Invoker("releaseBufferForReuse")
  void portalix$releaseBufferForReuse(GpuBuffer buffer);
}
