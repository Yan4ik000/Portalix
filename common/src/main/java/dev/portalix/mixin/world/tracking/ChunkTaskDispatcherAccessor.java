package dev.portalix.mixin.world.tracking;

import net.minecraft.server.level.ChunkTaskDispatcher;
import net.minecraft.server.level.ChunkTaskPriorityQueue;
import net.minecraft.util.thread.PriorityConsecutiveExecutor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkTaskDispatcher.class)
public interface ChunkTaskDispatcherAccessor {
  @Accessor("queue")
  ChunkTaskPriorityQueue portalix$queue();

  @Accessor("dispatcher")
  PriorityConsecutiveExecutor portalix$dispatcher();
}
