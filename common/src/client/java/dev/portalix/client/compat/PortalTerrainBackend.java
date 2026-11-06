package dev.portalix.client.compat;

import dev.portalix.client.compat.minecraft.VanillaSceneTerrain;
import dev.portalix.client.mixin.scene.LevelRendererAccessor;
import dev.portalix.client.portal.terrain.PortalOcclusionOpenings;
import dev.portalix.client.portal.terrain.PortalTerrainDemand;
import dev.portalix.client.portal.terrain.PortalTerrainGraphAccess;
import dev.portalix.client.portal.terrain.PortalFrameTerrainBudget;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.api.PortalEndpoint;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ChunkLoadingRenderState;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;

public final class PortalTerrainBackend {
  private static final TerrainIntegration SODIUM = TerrainIntegration.INSTANCE;
  private static long occlusionProofFrame = Long.MIN_VALUE;
  private static boolean primaryTerrainComplete;

  private PortalTerrainBackend() {}

  public static void attach(LevelRenderer renderer, LevelRenderer owner) {
    var target = (LevelRendererAccessor) renderer;
    target.portalix$sections(owner.sectionRenderDispatcher());
    if (SODIUM != null) {
      target.portalix$viewArea(owner.viewArea());
      SODIUM.attach(renderer, owner);
    } else VanillaSceneTerrain.attach(renderer, owner);
  }

  public static void detach(LevelRenderer renderer) {
    var target = (LevelRendererAccessor) renderer;
    if (SODIUM == null) renderer.sectionOcclusionGraph().waitAndReset(null);
    target.portalix$viewArea(null);
    target.portalix$sections(null);
    if (SODIUM != null) SODIUM.release(renderer);
  }

  public static void restorePrimary() {
    if (SODIUM != null) SODIUM.restorePrimary();
    else {
      var client = Minecraft.getInstance();
      client.levelRenderer.sectionRenderDispatcher().setCameraPosition(
          client.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.pos);
    }
  }

  public static void exchangePrimary(LevelRenderer renderer) {
    if (SODIUM != null) SODIUM.exchangePrimary(renderer);
    else VanillaSceneTerrain.exchangePrimary(renderer, Minecraft.getInstance().levelRenderer);
  }

  public static void retargetView(LevelRenderer renderer) {
    if (SODIUM != null) SODIUM.retarget(renderer);
  }

  public static void invalidateOcclusion(LevelRenderer renderer) {
    if (SODIUM != null) SODIUM.invalidateOcclusion(renderer);
    else {
      var graph = renderer.sectionOcclusionGraph();
      var area = renderer.viewArea();
      if (area != null)
        for (long node : PortalOcclusionOpenings.sections()) {
          var section = area.getRenderSectionAt(SectionPos.of(node).origin());
          if (section != null) graph.schedulePropagationFrom(section);
        }
      graph.invalidate();
    }
  }

  public static boolean usesVanillaChunkEvents() {
    return SODIUM == null;
  }

  public static boolean completeDemand(LevelRenderer renderer, CameraRenderState camera) {
    return SODIUM != null ? SODIUM.completeDemand(renderer, camera)
        : PortalTerrainDemand.vanillaComplete(renderer, camera);
  }

  public static boolean primaryOccludes(CameraRenderState camera, PortalEndpoint portal) {
    if (!camera.smartCull || PortalSceneManager.hasChunkChanges(
        camera.pos, Minecraft.getInstance().options.getEffectiveRenderDistance())) return false;
    var right = portal.right().scale(portal.width() / 2);
    var up = portal.up().scale(portal.height() / 2);
    var center = portal.center();
    double x = Math.abs(right.x) + Math.abs(up.x) + .5;
    double y = Math.abs(right.y) + Math.abs(up.y) + .5;
    double z = Math.abs(right.z) + Math.abs(up.z) + .5;
    var bounds = new AABB(center.x - x, center.y - y, center.z - z,
        center.x + x, center.y + y, center.z + z);
    var renderer = Minecraft.getInstance().levelRenderer;
    boolean occluded = SODIUM != null ? SODIUM.primaryOccludes(camera, bounds)
        : ((PortalTerrainGraphAccess) renderer.sectionOcclusionGraph()).portalix$occludes(camera, bounds);
    if (!occluded) return false;
    long frame = PortalFrameTerrainBudget.frame();
    if (occlusionProofFrame != frame) {
      occlusionProofFrame = frame;
      // Main-view uploads happen after portal rendering; an occluder changing this frame cannot hide a view.
      primaryTerrainComplete = Minecraft.getInstance().gameRenderer.gameRenderState()
          .levelRenderState.sectionUpdateRenderStates.isEmpty() && completeDemand(renderer, camera);
    }
    return primaryTerrainComplete;
  }

  public static void chunkLoaded(ClientLevel level, int x, int z) {
    if (SODIUM != null) SODIUM.loaded(level, x, z);
  }

  public static void chunkUnloaded(ClientLevel level, int x, int z) {
    if (SODIUM != null) SODIUM.unloaded(level, x, z);
  }

  public static void captureChanges(ClientChunkCache chunks, ChunkLoadingRenderState events) {
    events.removedLoadedChunks.clear();
    events.removedLoadedChunks.addAll(chunks.removedLoadedChunks());
    VanillaSceneTerrain.captureChanges(chunks, events);
  }

  public static void maintain(
      LevelRenderer renderer, CameraRenderState camera, ChunkLoadingRenderState events) {
    var access = (LevelRendererAccessor) renderer;
    access.portalix$reposition(camera);
    access.portalix$compile(camera);
    if (SODIUM == null)
      renderer
          .sectionOcclusionGraph()
          .update(camera, Minecraft.getInstance().options.fov().get(), events);
  }

  public static void unloadColumns(LevelRenderer renderer, LongSet removed) {
    if (SODIUM == null) VanillaSceneTerrain.unloadColumns(renderer, removed);
  }
}
