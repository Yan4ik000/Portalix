package dev.portalix.mixin.world.tracking;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.portalix.world.chunk.LoadingObserverTracker;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ChunkTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.server.level.DistanceManager$FixedPlayerDistanceChunkTracker")
abstract class PortalPlayerDistanceTrackerMixin extends ChunkTracker
    implements LoadingObserverTracker {
  @Unique private Long2IntMap portalix$observers = Long2IntMaps.EMPTY_MAP;
  @Unique private Long2IntMap portalix$delayed = Long2IntMaps.EMPTY_MAP;

  private PortalPlayerDistanceTrackerMixin() {
    super(34, 16, 256);
  }

  @Override
  public void update(long position, int level, boolean decreasing) {
    if (level == 0) level = portalix$delayed.getOrDefault(position, 0);
    super.update(position, Math.min(level, portalix$observers.getOrDefault(position, Integer.MAX_VALUE)), decreasing);
  }

  @ModifyReturnValue(method = "getLevelFromSource", at = @At("RETURN"))
  private int portalix$loadingSource(int original, long position) {
    if (original == 0) original = portalix$delayed.getOrDefault(position, 0);
    return Math.min(original, portalix$observers.getOrDefault(position, Integer.MAX_VALUE));
  }

  @Override
  public void portalix$observeLoading(Long2IntMap positions, Long2IntMap delayedSources) {
    if (positions == portalix$observers && delayedSources == portalix$delayed) return;
    var changed = new LongOpenHashSet();
    portalix$collectChanges(portalix$observers, positions, changed);
    portalix$collectChanges(portalix$delayed, delayedSources, changed);
    portalix$observers = positions;
    portalix$delayed = delayedSources;
    for (long key : changed) update(key, getLevelFromSource(key), false);
  }

  @Unique
  private static void portalix$collectChanges(Long2IntMap previous, Long2IntMap next, LongOpenHashSet changed) {
    if (previous == next) return;
    for (long key : previous.keySet())
      if (!next.containsKey(key)) changed.add(key);
    for (var entry : next.long2IntEntrySet())
      if (previous.getOrDefault(entry.getLongKey(), Integer.MAX_VALUE) != entry.getIntValue())
        changed.add(entry.getLongKey());
  }
}
