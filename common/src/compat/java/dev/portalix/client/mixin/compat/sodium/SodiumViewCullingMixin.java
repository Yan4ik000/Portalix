package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.compat.sodium.SodiumCullRequest;
import dev.portalix.client.compat.sodium.SodiumViewCollector;
import dev.portalix.client.compat.sodium.SodiumViewCulling;
import dev.portalix.client.compat.sodium.SodiumViewCullingState;
import dev.portalix.client.portal.scene.PortalSceneManager;
import java.util.List;
import java.util.Map;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.SectionInfoChange;
import net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager;
import net.caffeinemc.mods.sodium.client.render.chunk.async.CullTask;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.DeferredTaskList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.AsyncCameraTimingControl;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.CullType;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.SectionTree;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.storage.SectionStorage;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.render.viewport.ViewportProvider;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(value = RenderSectionManager.class, remap = false)
abstract class SodiumViewCullingMixin implements SodiumViewCulling {
  @Shadow @Final private ClientLevel level;
  @Shadow @Final private Map<CullType, SectionTree> cullResults;
  @Unique private boolean portalix$useOcclusion;
  @Shadow @Final private SectionStorage renderSections;
  @Shadow private CullTask pendingTask;
  @Shadow private DeferredTaskList taskLists;
  @Shadow private SortedRenderLists renderLists;
  @Shadow private SectionTree renderTree;
  @Shadow @Final public RenderRegionManager regions;
  @Shadow private boolean needsGraphUpdate;
  @Shadow private boolean needsRenderListUpdate;
  @Shadow private int frame;
  @Shadow private Vector3dc cameraPosition;
  @Unique private final ScopedValue<Boolean> portalix$localDirty = ScopedValue.newInstance();
  @Unique private boolean portalix$publishedPrimaryGraph;
  @Unique private boolean portalix$publishedPrimaryLists;

  @Unique private final SodiumViewCullingState portalix$views = new SodiumViewCullingState();

  @Override
  public SodiumViewCullingState portalix$cullingState() { return portalix$views; }

  @Shadow
  private void scheduleAsyncWork(Viewport viewport, FogParameters fog, boolean culling) {
    throw new AssertionError();
  }

  @Shadow
  private void consumeCullTaskResults(boolean wait) {
    throw new AssertionError();
  }

  @Shadow
  private void renderOutOfGraph(Viewport viewport, FogParameters fog) {
    throw new AssertionError();
  }

  @Override
  public void portalix$prepareView(Viewport viewport, FogParameters fog, boolean culling) {
    consumeCullTaskResults(false);
    var view = Minecraft.getInstance().levelRenderer;
    float distance = getSearchDistance(fog);
    var visibility = culling ? portalix$views.view(view, viewport, distance) : null;
    var tree = visibility == null ? null : visibility.tree();
    if (culling && (visibility == null || !visibility.fresh(SodiumCullRequest.plane(viewport))))
      scheduleAsyncWork(viewport, fog, true);
    var raster = PortalSceneManager.arrivalView()
        ? ((ViewportProvider) PortalSceneManager.renderFrustum()).sodium$createViewport() : viewport;
    long cameraRevision = PortalSceneManager.visibilityRevision();
    var cached = portalix$views.cachedLists(view, cameraRevision, distance, tree);
    if (cached != null) {
      renderLists = cached.lists();
      taskLists = cached.tasks();
      renderTree = cached.visibility();
      return;
    }
    if (tree == null) {
      renderOutOfGraph(viewport, fog);
      if (raster != viewport) {
        var collector = new SodiumViewCollector(regions, frame);
        renderTree.traverse(collector, raster, distance);
        renderLists = collector.createRenderLists(raster);
      }
    } else {
      var collector = new SodiumViewCollector(regions, frame);
      tree.traverse(collector, raster, distance);
      renderLists = collector.createRenderLists(raster);
      taskLists = visibility.tasks();
      renderTree = tree;
    }
    portalix$views.rememberLists(view, cameraRevision, distance, tree, renderTree, renderLists,
        taskLists, SodiumCullRequest.Footprint.of(viewport, getRenderDistance()));
  }

  @WrapMethod(method = "scheduleAsyncWork")
  private void portalix$sharedCullQueue(
      Viewport viewport, FogParameters fog, boolean culling, Operation<Void> original) {
    var view = PortalSceneManager.isPortalView() ? Minecraft.getInstance().levelRenderer : null;
    var next = portalix$views.schedule(view, new SodiumCullRequest(viewport, fog, culling, frame), pendingTask);
    if (next == null) return;
    var request = next.request();
    boolean primaryDirty = needsGraphUpdate;
    original.call(request.viewport(), request.fog(), request.culling());
    if (next.view() != null) needsGraphUpdate = primaryDirty;
  }

  @Inject(method = "scheduleAsyncWork", at = @At(value = "INVOKE",
      target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/storage/SectionStorage;startSafeReadPhase()V"), allow = 1)
  private void portalix$captureSnapshot(
      Viewport viewport, FogParameters fog, boolean culling, CallbackInfo ci) {
    portalix$views.started(pendingTask, renderTree, cullResults.values());
  }

  @ModifyArg(
      method = "scheduleAsyncWork",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/caffeinemc/mods/sodium/client/render/chunk/async/CullTask;<init>(Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;FFILnet/caffeinemc/mods/sodium/client/render/chunk/occlusion/OcclusionCuller;ZLnet/minecraft/world/level/Level;)V"),
      index = 3,
      allow = 1)
  private int portalix$capturedFrame(int frame) {
    return portalix$views.pendingFrame();
  }

  @WrapOperation(
      method = "prepareRenderTrees",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/caffeinemc/mods/sodium/client/render/chunk/async/CullTask;cancelIfNotStarted()Z"))
  private boolean portalix$keepQueuedView(CullTask task, Operation<Boolean> original) {
    return portalix$views.cancelPrimary(task);
  }

  @WrapMethod(method = "consumeCullTaskResults")
  private void portalix$collectViewCull(boolean wait, Operation<Void> original) {
    boolean primaryDirty = portalix$views.consume(pendingTask, wait, () -> {
      original.call(wait);
      return taskLists;
    }, result -> {
      cullResults.put(CullType.REGULAR, result.tree());
      taskLists = result.tasks();
      needsRenderListUpdate = true;
    }, this::portalix$finishCull, level);
    if (primaryDirty) needsGraphUpdate = true;
    portalix$views.dispatchNext(pendingTask,
        request -> scheduleAsyncWork(request.viewport(), request.fog(), request.culling()));
  }

  @Unique
  private void portalix$finishCull() {
    pendingTask = null;
    renderSections.endSafeReadPhase();
  }

  @Override
  public void portalix$releaseCulling(LevelRenderer view) {
    if (portalix$views.release(view, pendingTask)) portalix$finishCull();
  }

  @Override
  public void portalix$exchangeCulling(LevelRenderer view) {
    var source = cullResults.get(CullType.WIDE);
    if (source == null) source = cullResults.get(CullType.REGULAR);
    var destination = portalix$views.exchange(view, Minecraft.getInstance().levelRenderer,
        source, taskLists, cameraPosition);
    cullResults.clear();
    if (destination != null) {
      // The new main frustum traverses the transferred world visibility tree.
      cullResults.put(CullType.REGULAR, destination.tree());
      renderTree = destination.tree();
      taskLists = destination.tasks();
    }
    if (!portalix$views.primaryFresh()) needsGraphUpdate = true;
    needsRenderListUpdate = true;
  }

  @Override
  public boolean portalix$completeView(
      LevelRenderer renderer, CameraRenderState camera, boolean primary) {
    return portalix$views.completeView(renderer, camera, primary, cullResults.get(CullType.REGULAR),
        getRenderDistance(), renderSections, regions);
  }

  @Shadow
  private boolean isSectionEmpty(int x, int y, int z) {
    throw new AssertionError();
  }

  @Override
  public boolean portalix$primaryOccludes(CameraRenderState camera, AABB bounds) {
    var tree = cullResults.get(CullType.REGULAR);
    var snapshot = tree == null ? null : ((SodiumCullRequest.SnapshotAccess) tree).portalix$cullSnapshot();
    if (snapshot == null || snapshot != portalix$views.primarySnapshot()
        || !snapshot.visibilityFresh() || !snapshot.workFresh()) return false;
    var viewport = new Viewport(null, new Vector3d(camera.pos.x, camera.pos.y, camera.pos.z));
    if (!tree.isValidFor(viewport, getRenderDistance())
        || bounds.minY < level.getMinY() || bounds.maxY > level.getMaxY()) return false;
    return !tree.isBoxVisible(bounds.minX, bounds.minY, bounds.minZ,
        bounds.maxX, bounds.maxY, bounds.maxZ, this::isSectionEmpty);
  }

  @Inject(method = "markGraphDirty", at = @At("HEAD"))
  private void portalix$geometryChanged(CallbackInfo ci) {
    if (!portalix$localDirty.isBound()) portalix$views.graphChanged();
  }

  @Inject(method = "invalidateRenderLists", at = @At("HEAD"))
  private void portalix$listsChanged(CallbackInfo ci) {
    if (!portalix$localDirty.isBound()) portalix$views.listsChanged();
  }

  @WrapOperation(method = "markGraphDirty", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
      target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;needsGraphUpdate:Z"))
  private void portalix$localGraphWrite(
      RenderSectionManager manager, boolean dirty, Operation<Void> original) {
    if (!portalix$localDirty.isBound() || portalix$localDirty.get()) original.call(manager, dirty);
  }

  @WrapOperation(method = "invalidateRenderLists", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
      target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;needsRenderListUpdate:Z"))
  private void portalix$localListWrite(
      RenderSectionManager manager, boolean dirty, Operation<Void> original) {
    if (!portalix$localDirty.isBound() || portalix$localDirty.get()) original.call(manager, dirty);
  }

  @WrapOperation(method = {"onSectionAdded", "onSectionRemoved"}, at = @At(value = "INVOKE",
      target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;markGraphDirty()V"))
  private void portalix$sectionResidencyChanged(RenderSectionManager manager, Operation<Void> original,
      @Local(argsOnly = true, ordinal = 0) int x,
      @Local(argsOnly = true, ordinal = 1) int y,
      @Local(argsOnly = true, ordinal = 2) int z) {
    boolean primary = portalix$views.sectionChanged(
        x, y, z, SectionInfoChange.GRAPH | SectionInfoChange.RENDER_LIST, false);
    ScopedValue.where(portalix$localDirty, primary).run(() -> original.call(manager));
  }

  @WrapOperation(method = "upgradePendingUpdate", at = @At(value = "INVOKE",
      target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;markGraphDirty()V"))
  private void portalix$sectionWorkChanged(RenderSectionManager manager, Operation<Void> original,
      @Local(argsOnly = true) RenderSection section) {
    boolean primary = portalix$views.sectionChanged(
        section.getChunkX(), section.getChunkY(), section.getChunkZ(), SectionInfoChange.NONE, true);
    ScopedValue.where(portalix$localDirty, primary).run(() -> original.call(manager));
  }

  @WrapMethod(method = "processChunkBuilds")
  private void portalix$publicationBatch(
      Viewport viewport, UniformBufferManager uniforms, Operation<Void> original) {
    portalix$publishedPrimaryGraph = false;
    portalix$publishedPrimaryLists = false;
    original.call(viewport, uniforms);
  }

  @Inject(method = "updateWithResult", at = @At("RETURN"))
  private void portalix$sectionPublished(Viewport viewport, RenderSection section,
      ChunkBuildOutput output, List<RenderSection> patches, CallbackInfoReturnable<Integer> cir) {
    int changes = cir.getReturnValue();
    if (changes == SectionInfoChange.NONE) return;
    boolean graph = (changes & SectionInfoChange.GRAPH) != 0;
    boolean primary = portalix$views.sectionChanged(
        section.getChunkX(), section.getChunkY(), section.getChunkZ(), changes, false);
    portalix$publishedPrimaryGraph |= primary && graph;
    portalix$publishedPrimaryLists |= primary && (changes & SectionInfoChange.RENDER_LIST) != 0;
  }

  @WrapOperation(method = "processChunkBuilds", at = @At(value = "INVOKE",
      target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;markGraphDirty()V"))
  private void portalix$publishedGraph(RenderSectionManager manager, Operation<Void> original) {
    ScopedValue.where(portalix$localDirty, portalix$publishedPrimaryGraph)
        .run(() -> original.call(manager));
  }

  @WrapOperation(method = "processChunkBuilds", at = @At(value = "INVOKE",
      target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;invalidateRenderLists()V"))
  private void portalix$publishedLists(RenderSectionManager manager, Operation<Void> original) {
    ScopedValue.where(portalix$localDirty, portalix$publishedPrimaryLists)
        .run(() -> original.call(manager));
  }

  @WrapOperation(method = {"prepareRender", "consumeCullTaskResults"}, at = @At(value = "INVOKE",
      target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;invalidateRenderLists()V"))
  private void portalix$primaryLists(RenderSectionManager manager, Operation<Void> original) {
    ScopedValue.where(portalix$localDirty, true).run(() -> original.call(manager));
  }

  @Inject(method = "destroy", at = @At("HEAD"))
  private void portalix$stopCulling(CallbackInfo ci) {
    portalix$views.stop();
  }

  @Inject(method = "destroy", at = @At("RETURN"))
  private void portalix$clearCulling(CallbackInfo ci) {
    if (pendingTask != null) portalix$finishCull();
    portalix$views.clear();
  }

  @Shadow
  private SectionTree findBestTree(Viewport viewport, FogParameters fog) {
    throw new AssertionError();
  }

  @Shadow
  private float getSearchDistance(FogParameters fog) {
    throw new AssertionError();
  }

  @Shadow
  private float getRenderDistance() {
    throw new AssertionError();
  }

  @Inject(method = "prepareRenderTrees", at = @At("HEAD"))
  private void portalix$retainDestinations(
      Viewport viewport, FogParameters fog, boolean cull, CallbackInfo ci) {
    portalix$useOcclusion = cull;
    portalix$views.retainDestinations(level, viewport, getRenderDistance(),
        cullResults.get(CullType.WIDE), IrisShaders.shadowPass());
  }

  @WrapOperation(method = "notifyChangedCamera", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
      target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;cameraStableSince:I"))
  private void portalix$primaryCameraStability(
      RenderSectionManager manager, int stableSince, Operation<Void> original) {
    // Iris builds shadow lists synchronously; its camera must not age primary async culls.
    if (!IrisShaders.shadowPass()) original.call(manager, stableSince);
  }

  @Inject(method = "findBestTree", at = @At("RETURN"), cancellable = true)
  private void portalix$returnTree(
      Viewport viewport, FogParameters fog, CallbackInfoReturnable<SectionTree> cir) {
    var selected = cir.getReturnValue();
    if (selected != null) {
      var fresh = portalix$views.preferFreshTree(selected, cullResults, viewport,
          getSearchDistance(fog), getRenderDistance());
      if (fresh != selected) cir.setReturnValue(fresh);
    }
    if (cir.getReturnValue() == null && portalix$useOcclusion) {
      var tree = portalix$views.reusableTree(viewport, getSearchDistance(fog));
      if (tree != null) cir.setReturnValue(tree);
    }
  }

  @WrapOperation(
      method = "finalizeRenderLists",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/caffeinemc/mods/sodium/client/render/chunk/occlusion/AsyncCameraTimingControl;getShouldRenderSync(Lnet/minecraft/client/Camera;)Z"))
  private boolean portalix$reuseVisibility(
      AsyncCameraTimingControl control,
      Camera camera,
      Operation<Boolean> original,
      @Local(argsOnly = true) Viewport viewport,
      @Local(argsOnly = true) FogParameters fog) {
    boolean synchronous = original.call(control, camera);
    return synchronous && (!portalix$useOcclusion || findBestTree(viewport, fog) == null);
  }

  @Inject(method = "updateSectionInfo", at = @At("RETURN"))
  private void portalix$patchRetained(
      RenderSection section, BuiltSectionInfo info, CallbackInfoReturnable<Integer> cir) {
    if (info != null && !section.isInvisible()) portalix$views.patchRetained(section);
  }
}
