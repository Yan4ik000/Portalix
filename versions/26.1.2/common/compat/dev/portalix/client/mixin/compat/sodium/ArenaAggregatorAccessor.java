package dev.portalix.client.mixin.compat.sodium;

import net.caffeinemc.mods.sodium.client.gl.arena.ArenaAggregator;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlMutableBuffer;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(value = ArenaAggregator.class, remap = false)
public interface ArenaAggregatorAccessor {
  @Invoker("releaseBufferForReuse")
  void portalix$releaseBufferForReuse(CommandList commands, GlMutableBuffer buffer);
}
