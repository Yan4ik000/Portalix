package dev.portalix.client.compat.sodium;

import dev.portalix.client.mixin.compat.sodium.RenderRegionAccessor;
import dev.portalix.client.mixin.compat.sodium.VisibleChunkCollectorAccessor;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.caffeinemc.mods.sodium.client.render.chunk.LocalSectionIndex;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.VisibleChunkCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;

public final class SodiumViewCollector extends VisibleChunkCollector {
  private final ReferenceOpenHashSet<ChunkRenderList> collected = new ReferenceOpenHashSet<>();
  private final int frame;

  public SodiumViewCollector(RenderRegionManager regions, int frame) {
    super(regions, frame);
    this.frame = frame;
  }

  @Override
  public void visit(int x, int y, int z) {
    var region =
        ((VisibleChunkCollectorAccessor) (Object) this).portalix$regions().getForChunk(x, y, z);
    if (region == null) return;
    int index =
        LocalSectionIndex.pack(
            x & (RenderRegion.REGION_WIDTH - 1),
            y & (RenderRegion.REGION_HEIGHT - 1),
            z & (RenderRegion.REGION_LENGTH - 1));
    var section = ((RenderRegionAccessor) region).portalix$sections()[index];
    // Retained visibility can outlive individual sections while unloads are published.
    if (section == null || section.isDisposed() || section.isInvisible()) return;
    var list = region.getRenderList();
    // Preparation and raster collection can rebuild the same view in one Sodium frame.
    if (collected.add(list)) {
      list.reset(frame);
      getUnsortedRenderLists().add(list);
    }
    list.add(index);
  }
}
