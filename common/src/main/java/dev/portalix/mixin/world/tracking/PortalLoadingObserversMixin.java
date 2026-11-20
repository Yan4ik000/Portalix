package dev.portalix.mixin.world.tracking;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.world.chunk.LoadingObserverTracker;
import dev.portalix.world.chunk.PortalChunkObservers;
import dev.portalix.world.chunk.PortalChunkPriorityUpdates;
import dev.portalix.world.chunk.PortalChunkTicketOrder;
import dev.portalix.world.chunk.PortalLoadingObservers;
import dev.portalix.world.chunk.WarmColumnAccess;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.LoadingChunkTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThrottlingChunkTaskDispatcher;
import net.minecraft.util.thread.StrictQueue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DistanceManager.class)
abstract class PortalLoadingObserversMixin implements PortalLoadingObservers {
  @Unique private Long2IntMap portalix$observers = Long2IntMaps.EMPTY_MAP;
  @Unique private final Set<ServerPlayer> portalix$crossingPlayers = new HashSet<>();
  @Unique private Long2IntMap portalix$delayed = Long2IntMaps.EMPTY_MAP;
  @Unique private long portalix$checkedTick = Long.MIN_VALUE;
  @Shadow @Final private Long2ObjectMap<ObjectSet<ServerPlayer>> playersPerChunk;
  @Shadow @Final private ThrottlingChunkTaskDispatcher ticketDispatcher;
  @Shadow @Final private LoadingChunkTracker loadingChunkTracker;
  @Unique private final PortalChunkPriorityUpdates portalix$priorities =
      new PortalChunkPriorityUpdates(
          task -> ((ChunkTaskDispatcherAccessor) ticketDispatcher).portalix$dispatcher()
              .schedule(new StrictQueue.RunnableWithPriority(0, task)),
          snapshot -> ((PortalChunkTicketOrder.Access)
              ((ChunkTaskDispatcherAccessor) ticketDispatcher).portalix$queue()).portalix$prioritize(
                  snapshot.isEmpty() ? null : PortalChunkObservers.loadingPriorities(snapshot)));

  @Override
  public WarmColumnAccess portalix$warmColumns() {
    return (WarmColumnAccess) loadingChunkTracker;
  }

  @Override
  public void portalix$observeLoading(Long2IntMap positions, List<PortalChunkObservers> priorities) {
    portalix$observers = positions;
    portalix$priorities.update(priorities);
  }

  @Override
  public void portalix$loadingDelayChanged(ServerPlayer player) {
    if (PlayerPortalVisibility.of(player).resumeLoadingAt() != 0)
      portalix$crossingPlayers.add(player);
    else portalix$crossingPlayers.remove(player);
    portalix$checkedTick = Long.MIN_VALUE;
  }

  @Inject(method = {"addPlayer", "removePlayer"}, at = @At("RETURN"))
  private void portalix$playersMoved(SectionPos pos, ServerPlayer player, CallbackInfo ci) {
    portalix$checkedTick = Long.MIN_VALUE;
  }

  @Inject(method = "updatePlayerTickets", at = @At("RETURN"))
  private void portalix$viewDistanceChanged(int distance, CallbackInfo ci) {
    portalix$checkedTick = Long.MIN_VALUE;
  }

  @Unique
  private Long2IntMap portalix$delayedSources() {
    if (portalix$crossingPlayers.isEmpty()) return portalix$delayed = Long2IntMaps.EMPTY_MAP;
    long now = portalix$crossingPlayers.iterator().next().level().getGameTime();
    if (now == portalix$checkedTick) return portalix$delayed;
    portalix$checkedTick = now;
    var next = new Long2IntOpenHashMap();
    var candidates = portalix$crossingPlayers.iterator();
    while (candidates.hasNext()) {
      var player = candidates.next();
      var chunkMap = player.level().getChunkSource().chunkMap;
      if (player.isRemoved() || chunkMap.getDistanceManager() != (Object) this
          || !PlayerPortalVisibility.of(player).loadingTransitionActive(now)) {
        candidates.remove();
        continue;
      }
      long center = player.getLastSectionPos().chunk().pack();
      var players = playersPerChunk.get(center);
      if (players == null || !players.contains(player) || next.containsKey(center)) continue;
      var map = (ChunkMapAccessor) chunkMap;
      int limit = map.portalix$serverViewDistance();
      int radius = 0;
      for (var observer : players) {
        int distance = map.portalix$playerViewDistance(observer);
        radius = Math.max(radius, PlayerPortalVisibility.of(observer).loadingRadius(now, distance, limit));
        if (radius == limit) break;
      }
      if (radius < limit) next.put(center, limit - radius);
    }
    if (!next.equals(portalix$delayed)) portalix$delayed = next.isEmpty() ? Long2IntMaps.EMPTY_MAP : next;
    return portalix$delayed;
  }

  @WrapOperation(
      method = "runAllUpdates",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/server/level/DistanceManager$PlayerTicketTracker;runAllUpdates()V"))
  private void portalix$updateLoadingObservers(@Coerce Object tracker, Operation<Void> original) {
    ((LoadingObserverTracker) tracker).portalix$observeLoading(portalix$observers, portalix$delayedSources());
    original.call(tracker);
  }
}
