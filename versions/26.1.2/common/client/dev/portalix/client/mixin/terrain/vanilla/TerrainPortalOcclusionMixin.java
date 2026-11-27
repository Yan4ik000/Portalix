package dev.portalix.client.mixin.terrain.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import dev.portalix.client.portal.terrain.PortalOcclusionOpenings;
import dev.portalix.client.portal.terrain.PortalGraphColumns;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkTrackingView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SectionOcclusionGraph.class)
abstract class TerrainPortalOcclusionMixin {
  @Shadow private ViewArea viewArea;

  @WrapOperation(
      method = "runUpdates",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/SectionOcclusionGraph;getRelativeFrom(JLnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;Lnet/minecraft/core/Direction;)Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;"))
  private RenderSection portalix$unloadedBoundary(
      SectionOcclusionGraph graph,
      long camera,
      RenderSection parent,
      Direction direction,
      Operation<RenderSection> original,
      @Local(argsOnly = true) SectionOcclusionGraph.GraphStorage storage,
      @Share("opening") LocalBooleanRef opening) {
    var section = original.call(graph, camera, parent, direction);
    if (section != null) {
      opening.set(PortalOcclusionOpenings.contains(parent.getSectionNode()));
      return section;
    }
    long neighbor = parent.getNeighborSectionNode(direction);
    int y = SectionPos.y(neighbor);
    int radius = viewArea.getViewDistance();
    if (y < viewArea.getLevelHeightAccessor().getMinSectionY()
        || y > viewArea.getLevelHeightAccessor().getMaxSectionY()
        || Math.abs(SectionPos.y(camera) - y) > radius
        || !ChunkTrackingView.isInViewDistance(
            SectionPos.x(camera),
            SectionPos.z(camera),
            radius,
            SectionPos.x(neighbor),
            SectionPos.z(neighbor))) return null;
    var waiting = ((PortalGraphColumns) storage).portalix$sectionsWaitingForChunkLoads()
        .computeIfAbsent(SectionPos.sectionToChunk(neighbor), ignored -> new LongArrayList());
    long node = parent.getSectionNode();
    if (!waiting.contains(node)) waiting.add(node);
    return null;
  }

  @WrapOperation(
      method = "runUpdates",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/chunk/SectionMesh;facesCanSeeEachother(Lnet/minecraft/core/Direction;Lnet/minecraft/core/Direction;)Z"))
  private boolean portalix$opening(
      SectionMesh mesh,
      Direction from,
      Direction to,
      Operation<Boolean> original,
      @Share("opening") LocalBooleanRef opening) {
    return opening.get() || original.call(mesh, from, to);
  }
}
