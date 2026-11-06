package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.compat.sodium.SharedSodiumTerrain;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.compat.sodium.SodiumColumnRetirement;
import dev.portalix.client.compat.sodium.SodiumRegionViews;
import dev.portalix.client.compat.sodium.SodiumSectionNeighbors;
import dev.portalix.client.compat.sodium.SodiumViewCulling;
import dev.portalix.client.compat.sodium.SodiumViewSortTask;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.client.portal.terrain.PortalFrameTerrainBudget;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;
import net.caffeinemc.mods.sodium.client.render.chunk.LocalSectionIndex;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation.JobDurationEstimator;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation.MeshTaskSizeEstimator;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation.UploadDurationEstimator;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation.UploadResourceBudget;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation.UnlimitedResourceBudget;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobResult;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.DeferredTaskList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.GraphDirection;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.SectionTree;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.storage.SectionStorage;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.caffeinemc.mods.sodium.client.util.MathUtil;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.ChunkPos;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = RenderSectionManager.class, remap = false)
abstract class SodiumSharedTerrainMixin implements SharedSodiumTerrain {
  @Shadow @Final private ClientLevel level;
  @Shadow @Final public RenderRegionManager regions;
  @Shadow @Final private SectionStorage renderSections;
  @Shadow @Final private ChunkBuilder builder;
  @Shadow @Final private int renderDistance;
  @Shadow @Final private JobDurationEstimator jobDurationEstimator;
  @Shadow @Final private MeshTaskSizeEstimator meshTaskSizeEstimator;
  @Shadow @Final private UploadDurationEstimator jobUploadDurationEstimator;

  @Unique
  private final ConcurrentLinkedDeque<ChunkJobResult<? extends BuilderTaskOutput>>
      portalix$urgentResults = new ConcurrentLinkedDeque<>();

  @Shadow private SortedRenderLists renderLists;
  @Shadow private SectionTree renderTree;
  @Shadow private DeferredTaskList taskLists;
  @Shadow private Vector3dc cameraPosition;
  @Shadow private int frame;
  @Unique private ChunkJobCollector portalix$frameJobs;
  @Unique private UploadResourceBudget portalix$frameUploads;
  @Unique private long portalix$budgetFrame = Long.MIN_VALUE;
  @Unique private long portalix$timingFrame = Long.MIN_VALUE;
  @Unique private boolean portalix$updateTiming;
  @Unique private int portalix$retainedDirections = -1;

  @Unique private final PriorityQueue<SodiumViewSortTask> portalix$sorts = new PriorityQueue<>();

  @Shadow
  public abstract void onChunkRemoved(int x, int z);

  @Override
  public void portalix$unloadChunks(LongCollection chunks) {
    var removed = new LongOpenHashSet(chunks);
    var columns = new Long2LongOpenHashMap();
    for (long chunk : chunks) {
      int x = ChunkPos.getX(chunk), z = ChunkPos.getZ(chunk);
      long column =
          ChunkPos.pack(x & ~RenderRegion.REGION_WIDTH_M, z & ~RenderRegion.REGION_LENGTH_M);
      int index = LocalSectionIndex.pack(x, 0, z) >> RenderRegion.REGION_HEIGHT_SH;
      columns.put(column, columns.get(column) | (1L << index));
    }
    var columnIterator = columns.long2LongEntrySet().fastIterator();
    while (columnIterator.hasNext()) {
      var entry = columnIterator.next();
      long column = entry.getLongKey();
      for (int y = level.getMinSectionY() & ~RenderRegion.REGION_HEIGHT_M;
          y <= level.getMaxSectionY();
          y += RenderRegion.REGION_HEIGHT) {
        var region = regions.getForChunk(ChunkPos.getX(column), y, ChunkPos.getZ(column));
        if (region != null)
          ((SodiumRegionViews) region).portalix$removeGeometry(entry.getLongValue());
      }
    }
    var storage = (SodiumColumnRetirement) renderSections;
    storage.portalix$beginColumns();
    try {
      for (long chunk : chunks) {
        int x = ChunkPos.getX(chunk), z = ChunkPos.getZ(chunk);
        int retained = 0;
        for (int direction = GraphDirection.NORTH; direction < GraphDirection.COUNT; direction++)
          if (!removed.contains(
              ChunkPos.pack(x + GraphDirection.x(direction), z + GraphDirection.z(direction))))
            retained |= 1 << direction;
        portalix$retainedDirections = retained;
        onChunkRemoved(x, z);
      }
    } finally {
      storage.portalix$endColumns();
      portalix$retainedDirections = -1;
    }
  }

  @Inject(method = "disconnectNeighborNodes", at = @At("HEAD"), cancellable = true)
  private void portalix$disconnectColumn(RenderSection section, CallbackInfo ci) {
    if (portalix$retainedDirections < 0) return;
    ((SodiumSectionNeighbors) section).portalix$disconnect(portalix$retainedDirections);
    ci.cancel();
  }

  @Shadow
  private void submitDeferredSectionTasks(
      ChunkJobCollector collector, UploadResourceBudget budget) {
    throw new AssertionError();
  }

  @Shadow
  private void submitSectionTask(
      ChunkJobCollector collector, RenderSection section, UploadResourceBudget budget) {
    throw new AssertionError();
  }

  @Inject(method = "prepareFrame", at = @At("HEAD"))
  private void portalix$physicalFrameTiming(Vector3dc position, CallbackInfo ci) {
    long current = PortalFrameTerrainBudget.frame();
    portalix$updateTiming = portalix$timingFrame != current;
    if (portalix$updateTiming) {
      portalix$timingFrame = current;
      portalix$frameJobs = null;
      portalix$frameUploads = null;
    }
  }

  @WrapOperation(
      method = "prepareFrame",
      at = {
        @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target =
            "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;lastFrameDuration:J"),
        @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target =
            "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;lastFrameAtTime:J"),
        @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target =
            "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;averageFrameDuration:J")
      })
  private void portalix$frameDuration(
      RenderSectionManager manager, long duration, Operation<Void> original) {
    if (portalix$updateTiming) original.call(manager, duration);
  }

  @Inject(method = "updateChunks", at = @At("HEAD"), cancellable = true)
  private void portalix$activeQueues(Viewport viewport, boolean immediate, CallbackInfo ci) {
    if (IrisShaders.shadowPass()) {
      ci.cancel();
      return;
    }
    long current = PortalFrameTerrainBudget.frame();
    if (portalix$budgetFrame == current && !immediate) {
      submitDeferredSectionTasks(portalix$frameJobs, portalix$frameUploads);
      ci.cancel();
      return;
    }
    portalix$budgetFrame = current;
    ((SodiumViewCulling) this).portalix$cullingState().pruneQueues(Minecraft.getInstance().levelRenderer);
    portalix$sorts.removeIf(
        task -> {
          if (task.cancelled()) return true;
          if (task.getRenderSection().isDisposed()
              || task.view != Minecraft.getInstance().levelRenderer
                  && !PortalSceneManager.isActiveView(task.view)) {
            task.result.cancel(false);
            return true;
          }
          return false;
        });
  }

  @Inject(method = "submitDeferredSectionTasks", at = @At("HEAD"), cancellable = true)
  private void portalix$nearestWork(
      ChunkJobCollector collector, UploadResourceBudget budget, CallbackInfo ci) {
    var views = ((SodiumViewCulling) this).portalix$cullingState();
    Vector3dc primary = cameraPosition;
    long now = System.nanoTime();
    boolean immediate = budget == UnlimitedResourceBudget.INSTANCE;
    try {
      while (collector.hasBudgetRemaining() && budget.isAvailable()) {
        var owner = Minecraft.getInstance().levelRenderer;
        DeferredTaskList next = taskLists != null && !taskLists.isEmpty()
            && (immediate || PortalFrameTerrainBudget.claim(owner).canPrepare()) ? taskLists : null;
        Vector3dc position = primary;
        for (var entry : views.terrainQueues()) {
          var view = entry.getValue();
          if (!view.tasks().isEmpty()
              && (immediate || PortalFrameTerrainBudget.claim(entry.getKey()).canPrepare())
              && (next == null || view.tasks().firstLong() < next.firstLong())) {
            next = view.tasks();
            position = view.camera();
            owner = entry.getKey();
          }
        }
        for (var entry : views.shadowQueues()) {
          var view = entry.getValue();
          if (!view.tasks().isEmpty()
              && (immediate || PortalFrameTerrainBudget.claim(entry.getKey()).canPrepare())
              && (next == null || view.tasks().firstLong() < next.firstLong())) {
            next = view.tasks();
            position = view.camera();
            owner = entry.getKey();
          }
        }
        var sort = portalix$sorts.peek();
        if (sort != null
            && (next == null
                || sort.priority(now)
                    < MathUtil.comparableIntToFloat((int) (next.firstLong() >> 32)))) {
          portalix$sorts.remove();
          if (sort.cancelled() || sort.getRenderSection().isDisposed()) continue;
          sort.calculateEstimations(
              jobDurationEstimator, meshTaskSizeEstimator, jobUploadDurationEstimator);
          sort.job = builder.scheduleTask(sort, false, collector::onJobFinished);
          sort.getRenderSection().addRunningJob(sort.job);
          collector.addSubmittedJob(sort.job);
          budget.consume(sort.getEstimatedUploadDuration(), sort.getEstimatedSize());
          continue;
        }
        if (next == null) break;
        var section = renderSections.getConsistent(next.dequeueNextSectionPos());
        if (section == null) {
          PortalFrameTerrainBudget.claim(owner).skipped();
        } else {
          cameraPosition = position;
          long start = System.nanoTime();
          try {
            submitSectionTask(collector, section, budget);
          } finally {
            PortalFrameTerrainBudget.claim(owner).prepared(System.nanoTime() - start);
          }
        }
      }
    } finally {
      cameraPosition = primary;
    }
    ci.cancel();
  }

  @Override
  public void portalix$queueSort(SodiumViewSortTask task) {
    task.prioritize(renderDistance * 16.0f);
    portalix$sorts.add(task);
    if (portalix$frameJobs != null)
      submitDeferredSectionTasks(portalix$frameJobs, portalix$frameUploads);
  }

  @Override
  public void portalix$queueShadowView() {
    if (taskLists == null) return;
    ((SodiumViewCulling) this).portalix$cullingState().queueShadow(
        Minecraft.getInstance().levelRenderer, taskLists, new Vector3d(cameraPosition));
    if (portalix$frameJobs != null)
      submitDeferredSectionTasks(portalix$frameJobs, portalix$frameUploads);
  }

  @ModifyArg(
      method = "updateChunks",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/executor/ChunkJobCollector;<init>(Ljava/util/function/Consumer;)V"),
      index = 0)
  private Consumer<ChunkJobResult<? extends BuilderTaskOutput>> portalix$urgentCollector(
      Consumer<ChunkJobResult<? extends BuilderTaskOutput>> original) {
    return portalix$urgentResults::add;
  }

  @WrapOperation(
      method = "collectChunkBuildResults",
      at = @At(value = "INVOKE", target =
          "Ljava/util/concurrent/ConcurrentLinkedDeque;poll()Ljava/lang/Object;"))
  private Object portalix$priorityResult(ConcurrentLinkedDeque<?> results, Operation<Object> original) {
    var urgent = portalix$urgentResults.poll();
    return urgent != null ? urgent : original.call(results);
  }

  @ModifyReturnValue(method = "collectChunkBuildResults", at = @At("RETURN"))
  private ArrayList<BuilderTaskOutput> portalix$publishViewSorts(ArrayList<BuilderTaskOutput> outputs) {
    // Sodium has already cleared running jobs and updated its estimators for every result.
    outputs.removeIf(output -> {
      if (!(output instanceof SodiumViewSortTask.Output sorted)) return false;
      sorted.publish();
      return true;
    });
    return outputs;
  }

  @Inject(method = "destroy", at = @At("HEAD"))
  private void portalix$cancelSorts(CallbackInfo ci) {
    for (var task : portalix$sorts) task.result.cancel(false);
    portalix$sorts.clear();
  }

  @Shadow
  public abstract void tickVisibleRenders();

  @Override
  public void portalix$portalView(
      Camera camera, Viewport viewport, FogParameters fog, boolean culling) {
    var primary = ((SodiumViewCulling) this).portalix$cullingState()
        .parkPrimary(renderTree, renderLists, taskLists, cameraPosition);
    var pos = camera.position();
    var next = new Vector3d(pos.x, pos.y, pos.z);
    cameraPosition = next;
    frame++;
    taskLists = primary.tasks();
    ((SodiumViewCulling) this).portalix$prepareView(viewport, fog, culling);
    ((SodiumViewCulling) this).portalix$cullingState().queueTerrain(
        Minecraft.getInstance().levelRenderer, taskLists, next);
    // All cameras feed one physical frame's queue. Results are published before drawing views.
    if (portalix$frameJobs != null)
      submitDeferredSectionTasks(portalix$frameJobs, portalix$frameUploads);
    tickVisibleRenders();
  }

  @Override
  public void portalix$restorePrimary() {
    var primary = ((SodiumViewCulling) this).portalix$cullingState().restorePrimary();
    if (primary == null) return;
    cameraPosition = primary.camera();
    renderTree = primary.tree();
    taskLists = primary.tasks();
    renderLists = primary.lists();
  }

  @Override
  public void portalix$exchangePrimary(LevelRenderer renderer) {
    portalix$restorePrimary();
    ((SodiumViewCulling) this).portalix$exchangeCulling(renderer);
    for (var region : regions.getLoadedRegions()) region.clearAllCachedBatches();
  }

  @WrapOperation(
      method = "updateChunks",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;submitSectionTasks(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/executor/ChunkJobCollector;Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/executor/ChunkJobCollector;Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/executor/ChunkJobCollector;Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/estimation/UploadResourceBudget;Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;)V"))
  private void portalix$viewJobs(
      RenderSectionManager manager,
      ChunkJobCollector immediate,
      ChunkJobCollector next,
      ChunkJobCollector deferred,
      UploadResourceBudget budget,
      Viewport viewport,
      Operation<Void> original) {
    portalix$frameJobs = deferred;
    portalix$frameUploads = budget;
    original.call(manager, immediate, next, deferred, budget, viewport);
  }
}
