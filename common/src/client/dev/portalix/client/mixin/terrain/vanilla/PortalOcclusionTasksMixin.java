package dev.portalix.client.mixin.terrain.vanilla;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.client.portal.terrain.PortalOcclusionContext;
import dev.portalix.client.portal.terrain.SharedOcclusionExecutor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.SectionOcclusionGraph.GraphStorage;
import net.minecraft.client.renderer.SectionOcclusionGraph.Node;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionOcclusionGraph.class)
abstract class PortalOcclusionTasksMixin {
  @Shadow private ViewArea viewArea;
  @Shadow @Final private LongOpenHashSet loadedChunks;

  @Shadow
  public abstract void invalidate();

  @ModifyExpressionValue(method = "invalidateIfNeeded", at = @At(value = "FIELD", target =
      "Lnet/minecraft/client/renderer/state/level/CameraRenderState;pos:Lnet/minecraft/world/phys/Vec3;"))
  private Vec3 portalix$invalidationOrigin(Vec3 eye) {
    return PortalSceneManager.terrainOrigin(eye);
  }

  @WrapOperation(method = "runPartialUpdate", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/renderer/SectionOcclusionGraph;runUpdates(Lnet/minecraft/client/renderer/SectionOcclusionGraph$GraphStorage;Lnet/minecraft/world/phys/Vec3;Ljava/util/Queue;ZLjava/util/function/Consumer;Lit/unimi/dsi/fastutil/longs/LongOpenHashSet;Lit/unimi/dsi/fastutil/longs/LongOpenHashSet;)V"))
  private void portalix$partialVisibility(
      SectionOcclusionGraph graph, GraphStorage storage, Vec3 eye, Queue<Node> queue, boolean smartCull,
      Consumer<RenderSection> onAdded, LongOpenHashSet empty, LongOpenHashSet loaded, Operation<Void> original) {
    var snapshot = ((PortalOcclusionContext.SnapshotAccess) storage).portalix$occlusionSnapshot();
    if (snapshot == null) original.call(graph, storage, eye, queue, smartCull, onAdded, empty, loaded);
    else PortalOcclusionContext.run(snapshot, () ->
        original.call(graph, storage, snapshot.origin(), queue, snapshot.smartCull(), onAdded, empty, loaded));
  }

  @Inject(method = "updateLoadedChunks", at = @At("TAIL"))
  private void portalix$residencyChanged(
      LongOpenHashSet added, LongOpenHashSet removed, CallbackInfo ci) {
    if (viewArea == null) return;
    var center = viewArea.getCameraSectionPos();
    int radius = viewArea.getViewDistance();
    if (!added.isEmpty()
        && (center.y() < viewArea.minSectionY()
            || center.y() > viewArea.maxSectionY()
            || !loadedChunks.contains(ChunkPos.pack(center.x(), center.z())))) {
      invalidate();
      return;
    }
    for (long chunk : added)
      if (ChunkPos.getX(chunk) == center.x() && ChunkPos.getZ(chunk) == center.z()) {
        invalidate();
        return;
      }
    for (long chunk : removed)
      if (ChunkTrackingView.isInViewDistance(
          center.x(), center.z(), radius, ChunkPos.getX(chunk), ChunkPos.getZ(chunk))) {
        invalidate();
        return;
      }
  }

  @WrapMethod(method = "scheduleFullUpdate")
  private void portalix$cameraSnapshot(CameraRenderState camera, Operation<Void> original) {
    var snapshot = new CameraRenderState();
    snapshot.pos = PortalSceneManager.terrainOrigin(camera.pos);
    snapshot.blockPos = BlockPos.containing(snapshot.pos);
    snapshot.smartCull = camera.smartCull;
    original.call(snapshot);
  }

  @WrapOperation(
      method = "scheduleFullUpdate",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ljava/util/concurrent/CompletableFuture;runAsync(Ljava/lang/Runnable;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
  private CompletableFuture<Void> portalix$visibility(
      Runnable task,
      Executor executor,
      Operation<CompletableFuture<Void>> original,
      @Local(argsOnly = true) CameraRenderState camera) {
    var snapshot = PortalOcclusionContext.capture(camera.pos, viewArea.getViewDistance(), camera.smartCull);
    return original.call(
        (Runnable) () -> PortalOcclusionContext.run(snapshot, task),
        SharedOcclusionExecutor.executor());
  }
}
