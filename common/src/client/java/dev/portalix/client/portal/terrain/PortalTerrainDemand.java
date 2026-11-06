package dev.portalix.client.portal.terrain;

import dev.portalix.client.compat.minecraft.SceneExtractorAccess;
import dev.portalix.client.portal.scene.PortalSceneManager;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ChunkLoadingRenderState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

public final class PortalTerrainDemand {
  private static final ChunkSectionLayer[] LAYERS = ChunkSectionLayer.values();

  private PortalTerrainDemand() {}

  public static boolean affectedBy(
      ChunkLoadingRenderState events, Vec3 position, int viewDistance) {
    var center = SectionPos.of(position);
    int radius = viewDistance + 1;
    // Connectivity can enter from any side, including just beyond the visible boundary.
    return changedColumns(events.addedLoadedChunks, center, radius)
        || changedColumns(events.removedLoadedChunks, center, radius)
        || changedSections(events.addedEmptySections, center, radius)
        || changedSections(events.removedEmptySections, center, radius);
  }

  private static boolean changedColumns(LongSet columns, SectionPos center, int radius) {
    for (long column : columns)
      if (Math.abs(ChunkPos.getX(column) - center.x()) <= radius
          && Math.abs(ChunkPos.getZ(column) - center.z()) <= radius) return true;
    return false;
  }

  private static boolean changedSections(LongSet sections, SectionPos center, int radius) {
    for (long section : sections)
      if (Math.abs(SectionPos.x(section) - center.x()) <= radius
          && Math.abs(SectionPos.y(section) - center.y()) <= radius
          && Math.abs(SectionPos.z(section) - center.z()) <= radius) return true;
    return false;
  }

  public static boolean resident(CameraRenderState camera) {
    var client = Minecraft.getInstance();
    var level = client.level;
    if (level == null || camera.cullFrustum == null) return false;
    int radius = client.options.getEffectiveRenderDistance();
    var center = SectionPos.of(camera.pos);
    var chunks = level.getChunkSource();
    for (int x = center.x() - radius; x <= center.x() + radius; x++)
      for (int z = center.z() - radius; z <= center.z() + radius; z++) {
        if (!ChunkTrackingView.isInViewDistance(center.x(), center.z(), radius, x, z)
            || chunks.getChunk(x, z, ChunkStatus.FULL, false) != null) continue;
        if (visibleColumn(level, camera, x, z)) return false;
      }
    return true;
  }

  public static boolean vanillaComplete(LevelRenderer renderer, CameraRenderState camera) {
    var graph = (PortalTerrainGraphAccess) renderer.sectionOcclusionGraph();
    if (PortalSceneManager.hasPendingTerrainChanges()
        || !graph.portalix$settled(camera.pos) || !resident(camera)) return false;
    var client = Minecraft.getInstance();
    for (long column : renderer.sectionOcclusionGraph().expectedChunks())
      if (visibleColumn(client.level, camera, ChunkPos.getX(column), ChunkPos.getZ(column)))
        return false;
    var dirty = (SceneExtractorAccess) client.levelExtractor;
    for (var section : renderer.visibleSections()) {
      long node = section.getSectionNode();
      var chunk = client.level.getChunkSource().getChunk(
          SectionPos.x(node), SectionPos.z(node), ChunkStatus.FULL, false);
      if (chunk == null) return false;
      var mesh = section.getSectionMesh();
      if (mesh == CompiledSectionMesh.EMPTY
          && chunk.getSection(chunk.getSectionIndexFromSectionY(SectionPos.y(node))).hasOnlyAir())
        continue;
      if (!(mesh instanceof CompiledSectionMesh compiled)
          || ((SectionMeshTaskAccess) mesh).portalix$cancelled()
          || dirty.portalix$dirtyTerrain(node)) return false;
      for (var layer : LAYERS)
        if (!compiled.isEmpty(layer)
            && (!compiled.isVertexBufferUploaded(layer)
                || !compiled.isIndexBufferUploaded(layer))) return false;
    }
    return !PortalSceneManager.hasPendingTerrainChanges() && graph.portalix$settled(camera.pos);
  }

  private static boolean visibleColumn(
      ClientLevel level, CameraRenderState camera, int x, int z) {
    return camera.cullFrustum.isVisible(
        new AABB(x * 16.0, level.getMinY(), z * 16.0,
            x * 16.0 + 16, level.getMaxY() + 1, z * 16.0 + 16));
  }
}
