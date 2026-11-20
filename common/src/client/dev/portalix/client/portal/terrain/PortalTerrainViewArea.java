package dev.portalix.client.portal.terrain;

import dev.portalix.client.mixin.terrain.ViewAreaAccessor;
import dev.portalix.client.portal.scene.PortalSceneManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.core.SectionPos;

public final class PortalTerrainViewArea extends ViewArea {
  private final ViewArea owner;
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

  public PortalTerrainViewArea(
      ViewArea owner,
      SectionRenderDispatcher dispatcher,
      SectionOcclusionGraph graph,
      SectionPos center) {
    super(
        dispatcher,
        owner.minY(),
        owner.maxY(),
        owner.minSectionY(),
        owner.maxSectionY(),
        owner.getViewDistance(),
        graph);
    radius = owner.getViewDistance();
    this.owner = owner;
    this.graph = graph;
    this.center = center;
    ((ViewAreaAccessor) (Object) this)
        .portalix$sections(((ViewAreaAccessor) owner).portalix$sections());
  }

  public ViewArea owner() {
    return owner;
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
  public boolean repositionCamera(SectionPos position) {
    if (!PortalSceneManager.isPortalView())
      ((ViewAreaAccessor) owner).portalix$sections().repositionCenter(position);
    if (center.equals(position)) return false;
    center = position;
    graph.invalidate();
    return true;
  }

  @Override
  public SectionPos getCameraSectionPos() {
    return PortalOcclusionContext.cameraSection(center);
  }

  @Override
  public void releaseAllBuffers() {
    if (!PortalSceneManager.isPortalView()) owner.releaseAllBuffers();
  }
}
