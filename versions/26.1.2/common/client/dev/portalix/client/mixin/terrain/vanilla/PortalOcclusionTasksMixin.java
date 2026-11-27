package dev.portalix.client.mixin.terrain.vanilla;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.client.portal.terrain.PortalGraphColumns;
import dev.portalix.client.portal.terrain.PortalOcclusionContext;
import dev.portalix.client.portal.terrain.PortalTerrainViewArea;
import dev.portalix.client.portal.terrain.SharedOcclusionExecutor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.SectionOcclusionGraph.GraphEvents;
import net.minecraft.client.renderer.SectionOcclusionGraph.GraphStorage;
import net.minecraft.client.renderer.SectionOcclusionGraph.Node;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionOcclusionGraph.class)
abstract class PortalOcclusionTasksMixin {
  @Shadow private ViewArea viewArea;
  @Shadow public abstract void invalidate();

  @Inject(method = "addNeighbors", at = @At("TAIL"))
  private void portalix$queueLoadedColumn(GraphEvents events, ChunkPos pos, CallbackInfo ci) {
    // Vanilla sends these events to both the published and the pending graph.
    events.chunksWhichReceivedNeighbors().add(pos.pack());
  }

  @Inject(method = "queueSectionsWithNewNeighbors", at = @At("HEAD"))
  private void portalix$resumeLoadedColumns(@Coerce Object state, CallbackInfo ci) {
    if (!(viewArea instanceof PortalTerrainViewArea view)) return;
    var graph = (TerrainGraphStateAccessor) state;
    var waiting = ((PortalGraphColumns) graph.portalix$storage()).portalix$sectionsWaitingForChunkLoads();
    if (waiting.isEmpty()) return;
    var events = graph.portalix$events();
    var chunks = ((ClientLevel) view.getLevelHeightAccessor()).getChunkSource();
    var arrivals = events.chunksWhichReceivedNeighbors().iterator();
    while (arrivals.hasNext()) {
      long column = arrivals.nextLong();
      var parents = waiting.get(column);
      if (parents == null || !chunks.hasChunk(ChunkPos.getX(column), ChunkPos.getZ(column))) continue;
      waiting.remove(column);
      var sections = parents.iterator();
      while (sections.hasNext()) {
        var parent = view.storage().getExisting(sections.nextLong());
        if (parent != null) events.sectionsToPropagateFrom().add(parent);
      }
    }
  }

  @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/Camera;position()Lnet/minecraft/world/phys/Vec3;"))
  private Vec3 portalix$terrainOrigin(Vec3 eye) {
    return PortalSceneManager.terrainOrigin(eye);
  }

  @ModifyExpressionValue(method = "initializeQueueForFullUpdate", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/Camera;blockPosition()Lnet/minecraft/core/BlockPos;"))
  private BlockPos portalix$cameraSnapshot(BlockPos position) {
    var snapshot = PortalOcclusionContext.current();
    return snapshot == null ? position : BlockPos.containing(snapshot.origin());
  }

  @WrapOperation(method = "runPartialUpdate", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/renderer/SectionOcclusionGraph;runUpdates(Lnet/minecraft/client/renderer/SectionOcclusionGraph$GraphStorage;Lnet/minecraft/world/phys/Vec3;Ljava/util/Queue;ZLjava/util/function/Consumer;Lit/unimi/dsi/fastutil/longs/LongOpenHashSet;)V"))
  private void portalix$partialVisibility(SectionOcclusionGraph graph, GraphStorage storage,
      Vec3 eye, Queue<Node> queue, boolean smartCull, Consumer<RenderSection> onAdded,
      LongOpenHashSet empty, Operation<Void> original) {
    var snapshot = ((PortalOcclusionContext.SnapshotAccess) storage).portalix$occlusionSnapshot();
    if (snapshot == null) original.call(graph, storage, eye, queue, smartCull, onAdded, empty);
    else PortalOcclusionContext.run(snapshot, () ->
        original.call(graph, storage, snapshot.origin(), queue, snapshot.smartCull(), onAdded, empty));
  }

  @Inject(method = "onChunkReadyToRender", at = @At("TAIL"))
  private void portalix$reseed(ChunkPos pos, CallbackInfo ci) {
    if (viewArea == null) return;
    var center = viewArea.getCameraSectionPos();
    var height = viewArea.getLevelHeightAccessor();
    boolean outsideHeight = center.y() < height.getMinSectionY() || center.y() > height.getMaxSectionY();
    // A new seed can be disconnected from every parent already in the graph.
    if ((center.x() == pos.x() && center.z() == pos.z()) || (outsideHeight
        && ChunkTrackingView.isInViewDistance(
            center.x(), center.z(), viewArea.getViewDistance(), pos.x(), pos.z()))) invalidate();
  }

  @WrapOperation(method = "scheduleFullUpdate", at = @At(value = "INVOKE", target =
      "Ljava/util/concurrent/CompletableFuture;runAsync(Ljava/lang/Runnable;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
  private CompletableFuture<Void> portalix$visibility(Runnable task, Executor executor,
      Operation<CompletableFuture<Void>> original, @Local(argsOnly = true) Vec3 origin,
      @Local(argsOnly = true) boolean smartCull) {
    var snapshot = PortalOcclusionContext.capture(origin, viewArea.getViewDistance(), smartCull);
    return original.call((Runnable) () -> PortalOcclusionContext.run(snapshot, task), SharedOcclusionExecutor.executor());
  }
}
