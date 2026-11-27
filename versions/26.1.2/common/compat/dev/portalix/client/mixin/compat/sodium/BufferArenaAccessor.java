package dev.portalix.client.mixin.compat.sodium;

import net.caffeinemc.mods.sodium.client.gl.arena.ArenaAggregator;
import net.caffeinemc.mods.sodium.client.gl.arena.BufferArena;
import net.caffeinemc.mods.sodium.client.gl.arena.staging.StagingBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(value = BufferArena.class, remap = false)
public interface BufferArenaAccessor {
  @Accessor("parent")
  ArenaAggregator portalix$parent();

  @Accessor("stagingBuffer")
  StagingBuffer portalix$staging();
}
