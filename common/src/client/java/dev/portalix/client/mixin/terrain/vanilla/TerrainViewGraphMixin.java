package dev.portalix.client.mixin.terrain.vanilla;

import dev.portalix.client.portal.clip.PortalCullPlane;
import dev.portalix.client.portal.terrain.PortalInitialTerrainVisibility;
import dev.portalix.client.portal.terrain.PortalTerrainGraphAccess;
import dev.portalix.client.portal.terrain.PortalRenderSections;
import dev.portalix.client.mixin.terrain.ViewAreaAccessor;
import dev.portalix.client.world.RetainedChunkAccess;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import net.minecraft.client.Minecraft;
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
  @Unique private volatile Visibility portalix$visibility;
  @Unique private volatile PortalCullPlane portalix$clipPlane;

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
    var level = Minecraft.getInstance().level;
    var terrain = ((RetainedChunkAccess) level.getChunkSource()).portalix$terrainColumns();
    portalix$initial.collect(
        viewArea, terrain, camera, portalix$clipPlane, frustum, visible, nearby);
  }

  @Override
  public PortalCullPlane portalix$clipPlane() {
    return portalix$clipPlane;
  }

  @Override
  public void portalix$clipPlane(PortalCullPlane plane) {
    if (Objects.equals(plane, portalix$clipPlane)) return;
    portalix$clipPlane = plane;
    ((SectionOcclusionGraph) (Object) this).invalidate();
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
  public void portalix$built(SectionPos camera, PortalCullPlane plane) {
    portalix$visibility = new Visibility(currentGraph.get(), camera, plane);
  }

  @Override
  public boolean portalix$ready(Vec3 camera) {
    var visibility = portalix$visibility;
    return visibility != null
        && currentGraph.get() == visibility.graph()
        && Objects.equals(visibility.plane(), portalix$clipPlane)
        && SectionPos.of(camera).equals(visibility.camera());
  }

  @Override
  public boolean portalix$settled(Vec3 camera) {
    return portalix$ready(camera)
        && !needsFullUpdate
        && (fullUpdateTask == null || fullUpdateTask.isDone())
        && !needsFrustumUpdate.get()
        && nextSectionsToPropagateFrom == null
        && ((TerrainGraphStateAccessor) currentGraph.get()).portalix$propagation().isEmpty();
  }

  @Override
  public boolean portalix$occludes(CameraRenderState camera, AABB bounds) {
    if (!portalix$settled(camera.pos) || viewArea == null
        || Math.floor(camera.pos.x / 8) != prevCamX
        || Math.floor(camera.pos.y / 8) != prevCamY
        || Math.floor(camera.pos.z / 8) != prevCamZ) return false;
    var center = SectionPos.of(camera.pos);
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

  @Inject(method = "waitAndReset", at = @At("RETURN"))
  private void portalix$reset(CallbackInfo ci) {
    portalix$visibility = null;
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
