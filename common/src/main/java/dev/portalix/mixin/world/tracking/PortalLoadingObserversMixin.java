package dev.portalix.mixin.world.tracking;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.world.chunk.LoadingObserverTracker;
import dev.portalix.world.chunk.PortalLoadingObservers;
import dev.portalix.world.chunk.PortalChunkObservers;
import dev.portalix.world.chunk.PortalChunkPriorityUpdates;
import dev.portalix.world.chunk.PortalChunkTicketOrder;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import java.util.List;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ThrottlingChunkTaskDispatcher;
import net.minecraft.util.thread.StrictQueue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(DistanceManager.class)
abstract class PortalLoadingObserversMixin implements PortalLoadingObservers {
  @Unique private Long2IntMap portalix$observers = Long2IntMaps.EMPTY_MAP;
  @Shadow @Final private ThrottlingChunkTaskDispatcher ticketDispatcher;
  @Unique private final PortalChunkPriorityUpdates portalix$priorities =
      new PortalChunkPriorityUpdates(
          task -> ((ChunkTaskDispatcherAccessor) ticketDispatcher).portalix$dispatcher()
              .schedule(new StrictQueue.RunnableWithPriority(0, task)),
          snapshot -> ((PortalChunkTicketOrder.Access)
              ((ChunkTaskDispatcherAccessor) ticketDispatcher).portalix$queue()).portalix$prioritize(
                  snapshot.isEmpty() ? null : PortalChunkObservers.loadingPriorities(snapshot)));

  @Override
  public void portalix$observeLoading(Long2IntMap positions, List<PortalChunkObservers> priorities) {
    portalix$observers = positions;
    portalix$priorities.update(priorities);
  }

  @WrapOperation(
      method = "runAllUpdates",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/server/level/DistanceManager$PlayerTicketTracker;runAllUpdates()V"))
  private void portalix$updateLoadingObservers(@Coerce Object tracker, Operation<Void> original) {
    ((LoadingObserverTracker) tracker)
        .portalix$observeLoading(portalix$observers);
    original.call(tracker);
  }
}
