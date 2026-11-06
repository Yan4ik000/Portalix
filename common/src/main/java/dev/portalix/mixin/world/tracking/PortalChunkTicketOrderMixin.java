package dev.portalix.mixin.world.tracking;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.portalix.world.chunk.PortalChunkTicketOrder;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.List;
import java.util.function.LongToIntFunction;
import net.minecraft.server.level.ChunkTaskPriorityQueue;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkTaskPriorityQueue.class)
abstract class PortalChunkTicketOrderMixin implements PortalChunkTicketOrder.Access {
  @Shadow @Final private List<Long2ObjectLinkedOpenHashMap<List<Runnable>>> queuesPerPriority;
  @Shadow private volatile int topPriorityQueueIndex;
  @Unique private @Nullable PortalChunkTicketOrder portalix$order;

  @Override
  public void portalix$prioritize(@Nullable LongToIntFunction priority) {
    if (priority == null) portalix$order = null;
    else if (portalix$order == null) portalix$order = new PortalChunkTicketOrder(priority);
    else portalix$order.prioritize(priority);
  }

  @WrapMethod(method = "pop")
  private ChunkTaskPriorityQueue.@Nullable TasksForChunk portalix$nextTicket(
      Operation<ChunkTaskPriorityQueue.TasksForChunk> original) {
    if (portalix$order == null) return original.call();
    var selection = portalix$order.next(queuesPerPriority, topPriorityQueueIndex);
    if (selection == null) return original.call();
    var queue = queuesPerPriority.get(selection.level());
    ChunkTaskPriorityQueue.TasksForChunk result;
    if (selection.level() == topPriorityQueueIndex) {
      queue.getAndMoveToFirst(selection.position());
      result = original.call();
    } else {
      result = new ChunkTaskPriorityQueue.TasksForChunk(
          selection.position(), queue.remove(selection.position()));
    }
    portalix$order.retired(selection.level(), selection.position());
    return result;
  }

  @Inject(method = "submit", at = @At("TAIL"))
  private void portalix$submittedTicket(Runnable task, long position, int level, CallbackInfo ci) {
    if (portalix$order != null) portalix$order.submitted(level, position);
  }

  @WrapMethod(method = "resortChunkTasks")
  private void portalix$resortedTicket(int oldPriority, ChunkPos position, int newPriority,
      Operation<Void> original) {
    long key = position.pack();
    boolean moved = portalix$order != null && oldPriority < queuesPerPriority.size()
        && queuesPerPriority.get(oldPriority).containsKey(key);
    original.call(oldPriority, position, newPriority);
    if (moved) {
      portalix$order.retired(oldPriority, key);
      if (queuesPerPriority.get(newPriority).containsKey(key))
        portalix$order.submitted(newPriority, key);
    }
  }

  @Inject(method = "release", at = @At("TAIL"))
  private void portalix$retireTicket(long position, boolean unschedule, CallbackInfo ci) {
    if (portalix$order == null) return;
    for (int level = 0; level < queuesPerPriority.size(); level++)
      if (!queuesPerPriority.get(level).containsKey(position)) portalix$order.retired(level, position);
  }
}
