package dev.portalix.client.compat.minecraft;

import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.mixin.scene.LevelRendererAccessor;
import dev.portalix.client.mixin.terrain.ViewAreaAccessor;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.client.portal.terrain.PortalRenderSections;
import dev.portalix.client.portal.terrain.PortalTerrainGraphAccess;
import dev.portalix.client.portal.terrain.PortalTerrainViewArea;
import dev.portalix.client.portal.terrain.PortalViewIndicesAccess;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

public final class VanillaSceneTerrain {
  private VanillaSceneTerrain() {}

  public static ObjectArrayList<SectionRenderDispatcher.RenderSection> renderSections(
      ObjectArrayList<SectionRenderDispatcher.RenderSection> prepared) {
    if (!PortalSceneManager.arrivalView() || IrisShaders.shadowPass()) return prepared;
    var renderer = Minecraft.getInstance().levelRenderer;
    var area = (PortalTerrainViewArea) renderer.viewArea();
    return area.rasterSections(prepared, prepared == renderer.nearbyVisibleSections());
  }

  public static void attach(LevelRenderer renderer, LevelRenderer owner) {
    var target = (LevelRendererAccessor) renderer;
    var area = owner.viewArea();
    if (area instanceof PortalTerrainViewArea view) area = view.owner();
    var graph = renderer.sectionOcclusionGraph();
    var state = (PortalTerrainGraphAccess) graph;
    var center = SectionPos.of(PortalSceneManager.terrainOrigin(PortalSceneManager.camera().position()));
    int radius = PortalSceneManager.terrainRadius();
    if (renderer.viewArea() instanceof PortalTerrainViewArea view && view.owner() == area) {
      view.radius(radius);
      view.repositionCamera(center);
      return;
    }
    graph.waitAndReset(null);
    var view =
        new PortalTerrainViewArea(
            area,
            owner.sectionRenderDispatcher(),
            graph,
            center);
    view.radius(radius);
    target.portalix$viewArea(view);
    graph.waitAndReset(view);
    var primary = (PortalTerrainGraphAccess) owner.sectionOcclusionGraph();
    state.portalix$loadedChunks().addAll(primary.portalix$loadedChunks());
    state.portalix$emptySections().addAll(primary.portalix$emptySections());
  }

  public static void preparePrimary(LevelRenderer renderer, CameraRenderState camera) {
    var area = renderer.viewArea();
    if (area == null || !(area instanceof PortalTerrainViewArea) && !PortalSceneManager.limitedTerrain()) return;
    var view = primaryArea(renderer);
    view.radius(PortalSceneManager.terrainRadius());
    view.repositionCamera(SectionPos.of(PortalSceneManager.terrainOrigin(camera.pos)));
  }

  private static PortalTerrainViewArea primaryArea(LevelRenderer renderer) {
    var area = renderer.viewArea();
    if (area instanceof PortalTerrainViewArea view) return view;
    var graph = renderer.sectionOcclusionGraph();
    var view = new PortalTerrainViewArea(area, renderer.sectionRenderDispatcher(), graph, area.getCameraSectionPos());
    ((LevelRendererAccessor) renderer).portalix$viewArea(view);
    ((PortalTerrainGraphAccess) graph).portalix$viewArea(view);
    return view;
  }

  public static void exchangePrimary(LevelRenderer renderer, LevelRenderer primary) {
    var target = (LevelRendererAccessor) renderer;
    var source = (LevelRendererAccessor) primary;
    var sourceArea = primaryArea(primary);
    var sourceGraph = primary.sectionOcclusionGraph();

    var targetArea = renderer.viewArea();
    var targetGraph = renderer.sectionOcclusionGraph();
    source.portalix$viewArea(targetArea);
    source.portalix$graph(targetGraph);
    target.portalix$viewArea(sourceArea);
    target.portalix$graph(sourceGraph);

    var visible = primary.visibleSections();
    var nearby = primary.nearbyVisibleSections();
    source.portalix$visibleSections(renderer.visibleSections());
    source.portalix$nearbySections(renderer.nearbyVisibleSections());
    target.portalix$visibleSections(visible);
    target.portalix$nearbySections(nearby);

    var position = source.portalix$sortPosition();
    int iteration = source.portalix$sortIteration();
    source.portalix$sortPosition(target.portalix$sortPosition());
    source.portalix$sortIteration(target.portalix$sortIteration());
    target.portalix$sortPosition(position);
    target.portalix$sortIteration(iteration);

    var sourceIndices = (PortalViewIndicesAccess) primary;
    var targetIndices = (PortalViewIndicesAccess) renderer;
    var indices = sourceIndices.portalix$indices();
    sourceIndices.portalix$indices(targetIndices.portalix$indices());
    targetIndices.portalix$indices(indices);
  }

  public static void unloadColumns(LevelRenderer renderer, LongSet removed) {
    var area = renderer.viewArea();
    if (area == null) return;
    var sections =
        (PortalRenderSections<SectionRenderDispatcher.RenderSection>)
            ((ViewAreaAccessor) area).portalix$sections();
    ((SceneExtractorAccess) Minecraft.getInstance().levelExtractor)
        .portalix$unloadColumns(removed);
    for (long chunk : removed)
      sections.removeColumn(
          ChunkPos.getX(chunk), ChunkPos.getZ(chunk), SectionRenderDispatcher.RenderSection::reset);
  }
}
