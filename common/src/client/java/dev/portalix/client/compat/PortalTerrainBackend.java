package dev.portalix.client.compat;

import dev.portalix.client.compat.minecraft.VanillaSceneTerrain;
import dev.portalix.client.mixin.scene.LevelRendererAccessor;
import dev.portalix.client.portal.clip.PortalCullPlane;
import dev.portalix.client.portal.clip.PortalFrustumState;
import dev.portalix.client.portal.terrain.PortalOcclusionOpenings;
import dev.portalix.client.portal.terrain.PortalTerrainDemand;
import dev.portalix.client.portal.terrain.PortalTerrainGraphAccess;
import dev.portalix.client.portal.terrain.PortalFrameTerrainBudget;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.api.PortalEndpoint;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ChunkLoadingRenderState;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class PortalTerrainBackend {
  private static final TerrainIntegration SODIUM = TerrainIntegration.INSTANCE;
  private static long occlusionProofFrame = Long.MIN_VALUE;
  private static boolean primaryTerrainComplete;
  private static PrimaryDemand primaryDemand;

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
    invalidatePrimaryDemand();
    if (SODIUM != null) SODIUM.exchangePrimary(renderer);
    else VanillaSceneTerrain.exchangePrimary(renderer, Minecraft.getInstance().levelRenderer);
  }

  public static void retargetView(LevelRenderer renderer) {
    if (SODIUM != null) SODIUM.retarget(renderer);
  }

  public static void invalidateOcclusion(LevelRenderer renderer) {
    invalidatePrimaryDemand();
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
          .levelRenderState.sectionUpdateRenderStates.isEmpty() && primaryDemandReady(renderer, camera);
    }
    return primaryTerrainComplete;
  }

  public static void invalidatePrimaryDemand() {
    primaryDemand = null;
  }

  private static boolean primaryDemandReady(LevelRenderer renderer, CameraRenderState camera) {
    if (PortalSceneManager.hasPendingTerrainChanges()) return false;
    var frustum = camera.cullFrustum;
    var window = (PortalFrustumState) frustum;
    Object visibility = SODIUM == null
        ? ((PortalTerrainGraphAccess) renderer.sectionOcclusionGraph()).portalix$visibility()
        : null;
    var area = renderer.viewArea();
    int distance = Minecraft.getInstance().options.getEffectiveRenderDistance();
    if (primaryDemand != null
        && primaryDemand.matches(area, visibility, distance, camera, window)) return true;
    if (!completeDemand(renderer, camera)) {
      primaryDemand = null;
      return false;
    }
    primaryDemand = new PrimaryDemand(area, visibility, distance, camera.pos,
        frustum.getCamX(), frustum.getCamY(), frustum.getCamZ(),
        new Matrix4f(window.portalix$cullingMatrix()), window.portalix$exitPlane(),
        window.portalix$preparationCell());
    return true;
  }

  private record PrimaryDemand(
      ViewArea area, Object visibility, int distance, Vec3 position,
      double x, double y, double z, Matrix4f matrix, PortalCullPlane exit, Vec3 cell) {
    boolean matches(ViewArea area, Object visibility, int distance,
        CameraRenderState camera, PortalFrustumState window) {
      var frustum = camera.cullFrustum;
      return this.area == area && this.visibility == visibility && this.distance == distance
          && position.equals(camera.pos) && x == frustum.getCamX() && y == frustum.getCamY()
          && z == frustum.getCamZ() && matrix.equals(window.portalix$cullingMatrix())
          && Objects.equals(exit, window.portalix$exitPlane())
          && Objects.equals(cell, window.portalix$preparationCell());
    }
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
    if (!events.addedLoadedChunks.isEmpty() || !events.removedLoadedChunks.isEmpty()
        || !events.addedEmptySections.isEmpty() || !events.removedEmptySections.isEmpty())
      invalidatePrimaryDemand();
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
