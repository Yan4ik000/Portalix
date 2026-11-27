package dev.portalix.client.compat.minecraft;

import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.mixin.scene.LevelRendererAccessor;
import dev.portalix.client.portal.scene.PortalSceneManager;
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
    var access = (LevelRendererAccessor) renderer;
    return access.portalix$viewArea() instanceof PortalTerrainViewArea area
        ? area.rasterSections(prepared, prepared == access.portalix$nearbySections())
        : prepared;
  }

  public static void attach(LevelRenderer renderer, LevelRenderer owner) {
    var target = (LevelRendererAccessor) renderer;
    if (!(((LevelRendererAccessor) owner).portalix$viewArea() instanceof PortalTerrainViewArea main)) return;
    var area = main.owner();
    var graph = renderer.getSectionOcclusionGraph();
    var center = SectionPos.of(PortalSceneManager.terrainOrigin(PortalSceneManager.camera().position()));
    int radius = PortalSceneManager.terrainRadius();
    if (target.portalix$viewArea() instanceof PortalTerrainViewArea view && view.owner() == area) {
      view.radius(radius);
      view.repositionCamera(center);
    } else {
      graph.waitAndReset(null);
      var view = new PortalTerrainViewArea(owner.getSectionRenderDispatcher(), Minecraft.getInstance().level,
          radius, renderer, area, center);
      target.portalix$viewArea(view);
      graph.waitAndReset(view);
    }
  }

  public static void preparePrimary(LevelRenderer renderer, CameraRenderState camera) {
    var area = ((LevelRendererAccessor) renderer).portalix$viewArea();
    if (!(area instanceof PortalTerrainViewArea view)) return;
    view.radius(PortalSceneManager.terrainRadius());
    view.repositionCamera(SectionPos.of(PortalSceneManager.terrainOrigin(camera.pos)));
  }

  public static void exchangePrimary(LevelRenderer renderer, LevelRenderer primary) {
    var target = (LevelRendererAccessor) renderer;
    var source = (LevelRendererAccessor) primary;
    var sourceArea = source.portalix$viewArea();
    var sourceGraph = primary.getSectionOcclusionGraph();
    var targetArea = target.portalix$viewArea();
    var targetGraph = renderer.getSectionOcclusionGraph();
    source.portalix$viewArea(targetArea);
    source.portalix$graph(targetGraph);
    target.portalix$viewArea(sourceArea);
    target.portalix$graph(sourceGraph);

    var visible = primary.getVisibleSections();
    var nearby = source.portalix$nearbySections();
    source.portalix$visibleSections(renderer.getVisibleSections());
    source.portalix$nearbySections(target.portalix$nearbySections());
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
    var area = ((LevelRendererAccessor) renderer).portalix$viewArea();
    if (!(area instanceof PortalTerrainViewArea view)) return;
    for (long chunk : removed)
      view.storage().removeColumn(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), SectionRenderDispatcher.RenderSection::reset);
    renderer.getSectionOcclusionGraph().invalidate();
  }
}
