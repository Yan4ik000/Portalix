package dev.portalix.client.mixin.terrain.vanilla;

import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.client.portal.terrain.PortalInitialTerrainVisibility;
import dev.portalix.client.portal.terrain.PortalOcclusionContext;
import dev.portalix.client.portal.terrain.PortalTerrainGraphAccess;
import dev.portalix.client.portal.terrain.PortalRenderSections;
import dev.portalix.client.mixin.terrain.ViewAreaAccessor;
import dev.portalix.client.portal.terrain.PortalTerrainColumns;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionOcclusionGraph.class)
abstract class TerrainViewGraphMixin implements PortalTerrainGraphAccess {
  @Shadow @Final private LongOpenHashSet loadedChunks;
  @Shadow @Final private LongOpenHashSet emptySections;
  @Shadow private ViewArea viewArea;
  @Shadow @Final private AtomicReference<?> currentGraph;
  @Shadow private boolean needsFullUpdate;
  @Shadow private Future<?> fullUpdateTask;
  @Shadow private double prevCamX;
  @Shadow private double prevCamY;
  @Shadow private double prevCamZ;
  @Shadow @Final private AtomicBoolean needsFrustumUpdate;
  @Shadow private volatile BlockingQueue<RenderSection> nextSectionsToPropagateFrom;

  @Unique
  private final PortalInitialTerrainVisibility portalix$initial =
      new PortalInitialTerrainVisibility();

  @Override
  public void portalix$viewArea(ViewArea area) {
    viewArea = area;
  }

  @Override
  public void portalix$initialVisibility(
      CameraRenderState camera,
      Frustum frustum,
      List<RenderSection> visible,
      List<RenderSection> nearby) {
    portalix$initial.collect(viewArea, PortalTerrainColumns.current(), camera, frustum, visible, nearby);
  }

  @Override
  public LongSet portalix$loadedChunks() {
    return loadedChunks;
  }

  @Override
  public LongSet portalix$emptySections() {
    return emptySections;
  }

  @Override
  public PortalOcclusionContext.Snapshot portalix$visibility() {
    return portalix$snapshot((TerrainGraphStateAccessor) currentGraph.get());
  }

  @Unique
  private static PortalOcclusionContext.Snapshot portalix$snapshot(TerrainGraphStateAccessor state) {
    return state == null ? null : ((PortalOcclusionContext.SnapshotAccess) state.portalix$storage())
        .portalix$occlusionSnapshot();
  }

  @Override
  public boolean portalix$ready(Vec3 camera) {
    var snapshot = portalix$visibility();
    return snapshot != null && viewArea != null
        && snapshot.matchesView(PortalSceneManager.terrainOrigin(camera), viewArea.getViewDistance());
  }

  @Override
  public boolean portalix$settled(Vec3 camera) {
    if (viewArea == null || needsFullUpdate || fullUpdateTask != null && !fullUpdateTask.isDone()
        || needsFrustumUpdate.get() || nextSectionsToPropagateFrom != null) return false;
    var state = (TerrainGraphStateAccessor) currentGraph.get();
    var snapshot = portalix$snapshot(state);
    return snapshot != null
        && snapshot.matchesView(PortalSceneManager.terrainOrigin(camera), viewArea.getViewDistance())
        && state.portalix$propagation().isEmpty();
  }

  @Override
  public boolean portalix$occludes(CameraRenderState camera, AABB bounds) {
    var origin = PortalSceneManager.terrainOrigin(camera.pos);
    if (!portalix$settled(camera.pos)
        || Math.floor(origin.x / 8) != prevCamX
        || Math.floor(origin.y / 8) != prevCamY
        || Math.floor(origin.z / 8) != prevCamZ) return false;
    var center = SectionPos.of(origin);
    int radius = viewArea.getViewDistance();
    var sections = (PortalRenderSections<RenderSection>)
        ((ViewAreaAccessor) viewArea).portalix$sections();
    var graph = (SectionOcclusionGraph) (Object) this;
    for (int x = SectionPos.posToSectionCoord(bounds.minX); x <= SectionPos.posToSectionCoord(bounds.maxX); x++)
      for (int y = SectionPos.posToSectionCoord(bounds.minY); y <= SectionPos.posToSectionCoord(bounds.maxY); y++)
        for (int z = SectionPos.posToSectionCoord(bounds.minZ); z <= SectionPos.posToSectionCoord(bounds.maxZ); z++) {
          if (y < sections.minY() || y > sections.maxY() || Math.abs(y - center.y()) > radius
              || !ChunkTrackingView.isInViewDistance(center.x(), center.z(), radius, x, z)) return false;
          var section = sections.getExisting(SectionPos.asLong(x, y, z));
          if (section != null && graph.getNode(section) != null) return false;
        }
    return true;
  }

  @Inject(method = "schedulePropagationFrom", at = @At("HEAD"), cancellable = true)
  private void portalix$sharedMesh(
      SectionRenderDispatcher.RenderSection section, CallbackInfo ci) {
    if (viewArea == null) return;
    var center = viewArea.getCameraSectionPos();
    long node = section.getSectionNode();
    int radius = viewArea.getViewDistance();
    if (Math.abs(SectionPos.y(node) - center.y()) > radius
        || !ChunkTrackingView.isInViewDistance(
            center.x(), center.z(), radius, SectionPos.x(node), SectionPos.z(node))) ci.cancel();
  }
}
