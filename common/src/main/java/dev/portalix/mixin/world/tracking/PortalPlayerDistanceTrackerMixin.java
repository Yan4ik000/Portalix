package dev.portalix.mixin.world.tracking;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.portalix.world.chunk.LoadingObserverTracker;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import net.minecraft.server.level.ChunkTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.server.level.DistanceManager$FixedPlayerDistanceChunkTracker")
abstract class PortalPlayerDistanceTrackerMixin extends ChunkTracker
    implements LoadingObserverTracker {
  @Unique private Long2IntMap portalix$observers = Long2IntMaps.EMPTY_MAP;

  private PortalPlayerDistanceTrackerMixin() {
    super(34, 16, 256);
  }

  @Override
  public void update(long position, int level, boolean decreasing) {
    super.update(position, Math.min(level, portalix$observers.getOrDefault(position, Integer.MAX_VALUE)), decreasing);
  }

  @ModifyReturnValue(method = "getLevelFromSource", at = @At("RETURN"))
  private int portalix$loadingSource(int original, long position) {
    return Math.min(original, portalix$observers.getOrDefault(position, Integer.MAX_VALUE));
  }

  @Override
  public void portalix$observeLoading(Long2IntMap positions) {
    if (positions == portalix$observers) return;
    var previous = portalix$observers;
    portalix$observers = positions;
    for (long position : previous.keySet())
      if (!positions.containsKey(position)) update(position, getLevelFromSource(position), false);
    for (var entry : positions.long2IntEntrySet()) {
      long position = entry.getLongKey();
      int before = previous.getOrDefault(position, Integer.MAX_VALUE);
      if (before != entry.getIntValue())
        update(position, getLevelFromSource(position), entry.getIntValue() < before);
    }
  }
}
