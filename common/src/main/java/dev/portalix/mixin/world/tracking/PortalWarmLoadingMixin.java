package dev.portalix.mixin.world.tracking;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.portalix.world.chunk.WarmColumnAccess;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkTracker;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.LoadingChunkTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LoadingChunkTracker.class)
abstract class PortalWarmLoadingMixin extends ChunkTracker implements WarmColumnAccess {
  @Unique private static final int PORTALIX_BLOCK_TICKING = ChunkLevel.byStatus(FullChunkStatus.BLOCK_TICKING);
  @Unique private final Long2IntOpenHashMap portalix$holders = new Long2IntOpenHashMap();

  private PortalWarmLoadingMixin() {
    super(ChunkLevel.MAX_LEVEL + 2, 16, 256);
  }

  @ModifyReturnValue(method = "getLevelFromSource", at = @At("RETURN"))
  private int portalix$retainedSource(int nativeLevel, long key) {
    return portalix$level(key, nativeLevel);
  }

  @Unique
  private int portalix$level(long key, int level) {
    return !portalix$holders.isEmpty() && portalix$holders.containsKey(key) ? Math.min(level, PORTALIX_BLOCK_TICKING) : level;
  }

  @Override
  public void update(long key, int level, boolean decreasing) {
    super.update(key, portalix$level(key, level), decreasing);
  }

  @Override
  public void portalix$retainColumn(long key) {
    if (portalix$holders.addTo(key, 1) != 0) return;
    // A weaker source cannot change a settled level. Queued increases must still see the new pin.
    if (hasWork() || getLevel(key) > PORTALIX_BLOCK_TICKING)
      super.update(key, PORTALIX_BLOCK_TICKING, true);
  }

  @Override
  public void portalix$releaseColumn(long key) {
    if (portalix$holders.addTo(key, -1) == 1) {
      portalix$holders.remove(key);
      super.update(key, getLevelFromSource(key), false);
    }
  }
}
