package dev.portalix.client.portal.terrain;

import dev.portalix.client.mixin.terrain.ViewAreaAccessor;
import dev.portalix.client.portal.scene.PortalSceneManager;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;

public final class PortalTerrainViewArea extends ViewArea {
  private final ViewArea owner;
  private final SectionOcclusionGraph graph;
  private SectionPos center;

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
    this.owner = owner;
    this.graph = graph;
    this.center = center;
    ((ViewAreaAccessor) (Object) this)
        .portalix$sections(((ViewAreaAccessor) owner).portalix$sections());
  }

  public ViewArea owner() {
    return owner;
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
