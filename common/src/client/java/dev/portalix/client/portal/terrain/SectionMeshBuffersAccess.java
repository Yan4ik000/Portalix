package dev.portalix.client.portal.terrain;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSectionBufferSlice;
import net.minecraft.core.BlockPos;

/** Allocation snapshots published and read under the owning dispatcher's copy lock. */
public interface SectionMeshBuffersAccess {
  RenderSectionBufferSlice portalix$slice(ChunkSectionLayer layer);

  void portalix$slice(ChunkSectionLayer layer, RenderSectionBufferSlice slice);

  BlockPos portalix$origin();

  void portalix$origin(BlockPos origin);
}
