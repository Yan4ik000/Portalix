package dev.portalix.client.mixin.compat.sodium;

import net.caffeinemc.mods.sodium.client.gpu.arena.ArenaAggregator;
import net.caffeinemc.mods.sodium.client.gpu.arena.BufferArena;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(value = BufferArena.class, remap = false)
public interface BufferArenaAccessor {
  @Accessor("parent")
  ArenaAggregator portalix$parent();
}
