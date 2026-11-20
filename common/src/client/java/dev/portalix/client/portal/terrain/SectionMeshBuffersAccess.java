package dev.portalix.client.portal.terrain;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSectionBufferSlice;

/** Allocation snapshots published and read under the owning dispatcher's copy lock. */
public interface SectionMeshBuffersAccess {
  ChunkSectionLayer[] portalix$allocatedLayers();

  RenderSectionBufferSlice portalix$slice(ChunkSectionLayer layer);

  void portalix$slice(ChunkSectionLayer layer, RenderSectionBufferSlice slice);
}
