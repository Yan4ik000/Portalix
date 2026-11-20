package dev.portalix.test.client.mixin;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerChunkCache.class)
public interface ServerChunkCacheAccessor {
  @Invoker("broadcastChangedChunks")
  void portalix$broadcastChangedChunks(ProfilerFiller profiler);
}
