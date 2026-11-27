package dev.portalix.client.mixin.terrain.vanilla;

import dev.portalix.client.portal.terrain.PortalOcclusionContext;
import dev.portalix.client.portal.terrain.PortalGraphColumns;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.client.renderer.SectionOcclusionGraph.GraphStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(GraphStorage.class)
abstract class TerrainGraphBoundsMixin implements PortalOcclusionContext.SnapshotAccess, PortalGraphColumns {
  @Unique private final PortalOcclusionContext.Snapshot portalix$snapshot = PortalOcclusionContext.current();

  @Unique private final Long2ObjectMap<LongList> portalix$waitingForLoads = new Long2ObjectOpenHashMap<>();

  @Override
  public Long2ObjectMap<LongList> portalix$sectionsWaitingForChunkLoads() {
    return portalix$waitingForLoads;
  }

  @Override
  public PortalOcclusionContext.Snapshot portalix$occlusionSnapshot() {
    return portalix$snapshot;
  }
}
