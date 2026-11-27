package dev.portalix.client.portal.terrain;

import dev.portalix.client.portal.scene.PortalSceneManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

public final class PortalTerrainViewArea extends ViewArea {
  private final PortalTerrainViewArea owner;
  private final PortalRenderSections storage;
  private final SectionOcclusionGraph graph;
  private final RasterSections visible = new RasterSections();
  private final RasterSections nearby = new RasterSections();
  private SectionPos center;
  private volatile int radius;

  public void radius(int next) {
    if (radius == next) return;
    radius = next;
    graph.invalidate();
  }

  @Override
  public int getViewDistance() {
    return PortalOcclusionContext.radius(radius);
  }

  public PortalTerrainViewArea(SectionRenderDispatcher dispatcher, ClientLevel level,
      int radius, LevelRenderer renderer, PortalTerrainViewArea owner, SectionPos center) {
    super(dispatcher, level, radius, renderer);
    this.radius = radius;
    this.owner = owner;
    this.graph = renderer.getSectionOcclusionGraph();
    this.center = center;
    this.storage = owner == null
        ? new PortalRenderSections(level.getMinSectionY(), level.getMaxSectionY(), (index, node) -> dispatcher.new RenderSection(index, node),
            chunk -> level.getChunkSource().hasChunk(ChunkPos.getX(chunk), ChunkPos.getZ(chunk)))
        : owner.storage;
  }

  @Override
  protected void createSections(SectionRenderDispatcher dispatcher) {
    sections = new RenderSection[0];
  }

  public PortalTerrainViewArea owner() {
    return owner == null ? this : owner;
  }

  public PortalRenderSections storage() {
    return storage;
  }

  public int sectionCount() {
    int diameter = getViewDistance() * 2 + 1;
    return diameter * diameter * (storage.maxY() - storage.minY() + 1);
  }

  @Override
  public RenderSection getRenderSectionAt(BlockPos position) {
    return getRenderSection(SectionPos.asLong(position));
  }

  @Override
  public RenderSection getRenderSection(long node) {
    var position = getCameraSectionPos();
    int radius = getViewDistance();
    if (Math.abs(SectionPos.x(node) - position.x()) > radius
        || Math.abs(SectionPos.z(node) - position.z()) > radius) return null;
    return storage.getValue(node);
  }

  @Override
  public void setDirty(int x, int y, int z, boolean fromPlayer) {
    var section = storage.getExisting(SectionPos.asLong(x, y, z));
    if (section != null) section.setDirty(fromPlayer);
  }

  public ObjectArrayList<RenderSection> rasterSections(ObjectArrayList<RenderSection> prepared, boolean near) {
    return (near ? nearby : visible).select(prepared);
  }

  public void clearRasterSections() {
    visible.clear();
    nearby.clear();
  }

  private static final class RasterSections {
    private final ObjectArrayList<RenderSection> previous = new ObjectArrayList<>();
    private final ObjectArrayList<RenderSection> selected = new ObjectArrayList<>();
    private Camera camera;
    private long revision;

    void clear() {
      previous.clear();
      selected.clear();
      camera = null;
    }

    ObjectArrayList<RenderSection> select(ObjectArrayList<RenderSection> prepared) {
      var current = PortalSceneManager.camera();
      long nextRevision = PortalSceneManager.visibilityRevision();
      // World-coordinate sections retain their bounds. The graph can still replace
      // or reorder them in place while the camera remains stationary.
      if (camera == current && revision == nextRevision && previous.equals(prepared)) return selected;
      camera = current;
      revision = nextRevision;
      previous.clear();
      previous.addAll(prepared);
      selected.clear();
      var frustum = PortalSceneManager.renderFrustum();
      for (var section : prepared)
        if (frustum.isVisible(section.getBoundingBox())) selected.add(section);
      return selected;
    }
  }

  @Override
  public void repositionCamera(SectionPos position) {
    if (center.equals(position)) return;
    center = position;
    graph.invalidate();
  }

  @Override
  public SectionPos getCameraSectionPos() {
    return PortalOcclusionContext.cameraSection(center);
  }

  @Override
  public void releaseAllBuffers() {
    if (!PortalSceneManager.isPortalView()) storage.clear(RenderSection::reset);
  }
}
