package dev.portalix.client.mixin.terrain;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.client.portal.clip.PortalCullPlane;
import dev.portalix.client.portal.terrain.PortalOcclusionContext;
import dev.portalix.client.portal.terrain.PortalTerrainGraphAccess;
import dev.portalix.client.portal.terrain.SharedOcclusionExecutor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.SectionOcclusionGraph")
abstract class PortalOcclusionTasksMixin {
  @Shadow private ViewArea viewArea;
  @Shadow @Final private LongOpenHashSet loadedChunks;

  @Shadow
  public abstract void invalidate();

  @WrapMethod(method = "runPartialUpdate")
  private void portalix$partialVisibility(
      CameraRenderState camera, LongSet loaded, Operation<Void> original) {
    var plane = this instanceof PortalTerrainGraphAccess graph ? graph.portalix$clipPlane() : null;
    PortalOcclusionContext.run(plane, () -> original.call(camera, loaded));
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
    snapshot.pos = camera.pos;
    snapshot.blockPos = camera.blockPos.immutable();
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
    var position = SectionPos.of(camera.pos);
    var graph = this instanceof PortalTerrainGraphAccess access ? access : null;
    PortalCullPlane plane = graph == null ? null : graph.portalix$clipPlane();
    return original.call(
        (Runnable)
            () ->
                PortalOcclusionContext.run(
                    plane,
                    position,
                    () -> {
                      task.run();
                      if (graph != null) graph.portalix$built(position, plane);
                    }),
        SharedOcclusionExecutor.executor());
  }
}
