package dev.portalix.client.compat.sodium;

import dev.portalix.client.mixin.compat.sodium.RenderRegionAccessor;
import dev.portalix.client.mixin.compat.sodium.RenderSectionAccessor;
import dev.portalix.client.portal.clip.PortalCullPlane;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.client.portal.terrain.PortalTerrainDemand;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.SectionInfoChange;
import net.caffeinemc.mods.sodium.client.render.chunk.async.CullTask;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.DeferredTaskList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.CullType;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.SectionTree;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.storage.SectionStorage;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.render.viewport.ViewportProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkTrackingView;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class SodiumViewCullingState {
  private final LinkedHashMap<LevelRenderer, SodiumCullRequest> requests = new LinkedHashMap<>();
  private final Map<LevelRenderer, Visibility> views = new IdentityHashMap<>();
  private final Map<LevelRenderer, RenderLists> lists = new IdentityHashMap<>();
  private final Map<LevelRenderer, SodiumTerrainQueue> terrainQueues = new IdentityHashMap<>();
  private final Map<LevelRenderer, SodiumTerrainQueue> shadowQueues = new IdentityHashMap<>();
  private final Map<Long, SectionTree> returnTrees = new HashMap<>();
  private final Set<SodiumCullRequest.Snapshot> snapshots = new HashSet<>();
  private SodiumCullRequest.Snapshot primarySnapshot;
  private SodiumCullRequest.Footprint primaryFootprint;
  private long[] primaryDemand = new long[0];
  private long listRevision;
  private boolean closed;
  private Pending pending;
  private PrimaryView parkedPrimary;
  private boolean dispatching;

  // The native manager holds the only CullTask reference. This metadata follows that binding
  // from construction through cancellation/completion, even when its camera changes roles.
  private static final class Pending {
    final SodiumCullRequest request;
    LevelRenderer view;
    SodiumCullRequest.Snapshot snapshot;
    boolean nativePrimary;
    boolean discarded;

    Pending(Queued queued) {
      request = queued.request;
      view = queued.view;
      nativePrimary = view == null;
    }
  }

  public record PrimaryResult(SectionTree tree, DeferredTaskList tasks) {}

  public record PrimaryView(
      SectionTree tree, SortedRenderLists lists, DeferredTaskList tasks, Vector3dc camera) {}

  public record Queued(LevelRenderer view, SodiumCullRequest request) {}

  public record Visibility(SectionTree tree, DeferredTaskList tasks, long[] demand,
      PortalCullPlane plane, SodiumCullRequest.Snapshot snapshot) {
    public boolean fresh(PortalCullPlane plane) {
      return snapshot != null && snapshot.visibilityFresh() && snapshot.workFresh()
          && Objects.equals(this.plane, plane);
    }
  }

  public record RenderLists(long cameraRevision, long contentRevision, float distance,
      SectionTree source, SectionTree visibility, SortedRenderLists lists, DeferredTaskList tasks,
      SodiumCullRequest.Footprint footprint) {}

  public boolean discarding() { return pending != null && pending.discarded; }

  public Queued schedule(LevelRenderer view, SodiumCullRequest request, CullTask task) {
    if (closed) return null;
    if (!dispatching) requests.put(view, request);
    if (task != null) return null;
    var next = poll();
    if (next != null) pending = new Pending(next);
    return next;
  }

  public int pendingFrame() { return pending.request.frame(); }

  public void started(CullTask task, SectionTree current, Iterable<SectionTree> nativeTrees) {
    pending.snapshot = ((SodiumCullRequest.SnapshotAccess) task).portalix$cullSnapshot();
    trackSnapshots(pending.snapshot, current, nativeTrees);
  }

  public boolean cancelPrimary(CullTask task) {
    if (pending == null || !pending.nativePrimary || pending.view != null) return false;
    if (!task.cancelIfNotStarted()) return false;
    pending = null;
    return true;
  }

  public boolean consume(CullTask task, boolean wait, Supplier<DeferredTaskList> consumePrimary,
      Consumer<PrimaryResult> promote, Runnable finishPortal, ClientLevel level) {
    if (task == null || !wait && !task.isDone()) return false;
    var completed = pending;
    if (completed.nativePrimary && !completed.discarded && !closed) {
      var tasks = consumePrimary.get();
      publishPrimary(tasks, completed.snapshot, false);
      pending = null;
      return !primaryFresh();
    }
    try {
      var result = task.getResult();
      if (closed || completed.discarded) return false;
      // A virtual camera may start inside solid terrain. Its REGULAR result stays confined
      // to that origin until a new main cull can safely supply LOCAL/WIDE trees.
      var tree = result.getCullTreeRegular();
      var tasks = result.getPendingTaskLists();
      if (completed.view == null) {
        publishPrimary(tasks, completed.snapshot, completed.request.plane() != null);
        promote.accept(new PrimaryResult(tree, tasks));
        return !primaryFresh();
      } else {
        publish(completed.view, tree, tasks, completed.request.plane(), completed.snapshot, PortalTopology.links(level));
      }
      return false;
    } finally {
      pending = null;
      finishPortal.run();
    }
  }

  public void dispatchNext(CullTask task, Consumer<SodiumCullRequest> schedule) {
    if (closed || task != null || requests.isEmpty()) return;
    var next = requests.firstEntry().getValue();
    dispatching = true;
    try {
      schedule.accept(next);
    } finally {
      dispatching = false;
    }
  }

  private Queued poll() {
    var iterator = requests.entrySet().iterator();
    while (iterator.hasNext()) {
      var next = iterator.next();
      var view = next.getKey();
      var request = next.getValue();
      iterator.remove();
      if (view == null || PortalSceneManager.isActiveView(view)) return new Queued(view, request);
    }
    return null;
  }

  public Visibility view(LevelRenderer view, Viewport viewport, float distance) {
    var visibility = views.get(view);
    return visibility != null && visibility.tree.isValidFor(viewport, distance) ? visibility : null;
  }

  public SectionTree preferFreshTree(SectionTree selected, Map<CullType, SectionTree> trees,
      Viewport viewport, float fogDistance, float renderDistance) {
    if (selected == null) return null;
    var snapshot = ((SodiumCullRequest.SnapshotAccess) selected).portalix$cullSnapshot();
    if (snapshot == null || snapshot.visibilityFresh()) return selected;
    for (var type : CullType.NARROW_TO_WIDE) {
      var tree = trees.get(type);
      if (tree == null) continue;
      var candidate = ((SodiumCullRequest.SnapshotAccess) tree).portalix$cullSnapshot();
      if (candidate != null && candidate.visibilityFresh()
          && tree.isValidFor(viewport, type.isFogCulled ? fogDistance : renderDistance)) return tree;
    }
    return selected;
  }

  public RenderLists cachedLists(LevelRenderer view, long cameraRevision, float distance, SectionTree source) {
    var cached = lists.get(view);
    return cached != null && cached.cameraRevision == cameraRevision
        && cached.contentRevision == listRevision && cached.distance == distance && cached.source == source
        ? cached : null;
  }

  public void rememberLists(LevelRenderer view, long cameraRevision, float distance, SectionTree source,
      SectionTree visibility, SortedRenderLists renderLists, DeferredTaskList tasks,
      SodiumCullRequest.Footprint footprint) {
    lists.put(view, new RenderLists(cameraRevision, listRevision, distance, source, visibility,
        renderLists, tasks, footprint));
  }

  private void trackSnapshots(SodiumCullRequest.Snapshot pending, SectionTree current, Iterable<SectionTree> nativeTrees) {
    // Membership follows live tree ownership, independently of garbage collection.
    snapshots.clear();
    if (primarySnapshot != null) snapshots.add(primarySnapshot);
    if (parkedPrimary != null) trackTree(parkedPrimary.tree);
    if (pending != null) snapshots.add(pending);
    trackTree(current);
    for (var tree : nativeTrees) trackTree(tree);
    for (var tree : returnTrees.values()) trackTree(tree);
    for (var view : views.values()) if (view.snapshot != null) snapshots.add(view.snapshot);
    for (var cached : lists.values()) {
      trackTree(cached.source);
      trackTree(cached.visibility);
    }
  }

  private void trackTree(SectionTree tree) {
    if (tree == null) return;
    var snapshot = ((SodiumCullRequest.SnapshotAccess) tree).portalix$cullSnapshot();
    if (snapshot != null) snapshots.add(snapshot);
  }

  private void publish(LevelRenderer view, SectionTree tree, DeferredTaskList tasks,
      PortalCullPlane plane, SodiumCullRequest.Snapshot snapshot, List<PortalLink> links) {
    views.put(view, new Visibility(tree, tasks, ((SodiumTaskListAccess) tasks).portalix$sectionSnapshot(), plane, snapshot));
    for (var link : links) {
      var portal = link.entrance();
      var position = portal.endpoint().center();
      if (tree.isValidFor(new Viewport(null, new Vector3d(position.x, position.y, position.z)), 0))
        returnTrees.put(portal.identity(), tree);
    }
  }

  private void publishPrimary(DeferredTaskList tasks, SodiumCullRequest.Snapshot snapshot, boolean clipped) {
    // Native completion changes its task queue, not the draw tree/lists already extracted.
    if (parkedPrimary != null)
      parkedPrimary = new PrimaryView(parkedPrimary.tree, parkedPrimary.lists, tasks, parkedPrimary.camera);
    primaryDemand = ((SodiumTaskListAccess) tasks).portalix$sectionSnapshot();
    primarySnapshot = snapshot;
    if (clipped && snapshot != null) snapshot.invalidate();
  }

  public boolean primaryFresh() {
    return primarySnapshot != null && primarySnapshot.workFresh();
  }

  public SodiumCullRequest.Snapshot primarySnapshot() { return primarySnapshot; }

  public PrimaryView parkPrimary(SectionTree tree, SortedRenderLists lists,
      DeferredTaskList tasks, Vector3dc camera) {
    if (parkedPrimary == null) {
      parkedPrimary = new PrimaryView(tree, lists, tasks, camera);
      trackTree(tree);
    }
    return parkedPrimary;
  }

  public PrimaryView restorePrimary() {
    var primary = parkedPrimary;
    parkedPrimary = null;
    return primary;
  }

  public boolean release(LevelRenderer view, CullTask task) {
    lists.remove(view);
    requests.remove(view);
    views.remove(view);
    terrainQueues.remove(view);
    shadowQueues.remove(view);
    if (pending == null || pending.view != view) return false;
    pending.discarded = true;
    if (!task.cancelIfNotStarted()) return false;
    pending = null;
    return true;
  }

  public Visibility exchange(LevelRenderer view, LevelRenderer primary, SectionTree source,
      DeferredTaskList tasks, Vector3dc camera) {
    var destination = views.remove(view);
    if (source != null) views.put(view, new Visibility(source, tasks, primaryDemand, null, primarySnapshot));
    primaryDemand = destination == null ? new long[0] : destination.demand;
    primarySnapshot = destination == null ? null : destination.snapshot;
    primaryFootprint = primarySnapshot == null ? null : primarySnapshot.footprint();
    if (destination != null && destination.plane != null && primarySnapshot != null) primarySnapshot.invalidate();
    lists.remove(view);
    var primaryRequest = requests.remove(null);
    requests.remove(view);
    if (primaryRequest != null) requests.put(view, primaryRequest);
    if (pending != null) {
      if (pending.view == null) {
        pending.view = view;
        pending.nativePrimary = false;
      } else if (pending.view == view) {
        pending.view = null;
        pending.nativePrimary = false;
      }
    }
    terrainQueues.remove(view);
    if (tasks != null) terrainQueues.put(view, new SodiumTerrainQueue(tasks, camera));
    var primaryShadow = shadowQueues.remove(primary);
    var remoteShadow = shadowQueues.remove(view);
    if (primaryShadow != null) shadowQueues.put(view, primaryShadow);
    if (remoteShadow != null) shadowQueues.put(primary, remoteShadow);
    return destination;
  }

  public Set<Map.Entry<LevelRenderer, SodiumTerrainQueue>> terrainQueues() {
    return terrainQueues.entrySet();
  }

  public Set<Map.Entry<LevelRenderer, SodiumTerrainQueue>> shadowQueues() {
    return shadowQueues.entrySet();
  }

  public void queueTerrain(LevelRenderer view, DeferredTaskList tasks, Vector3dc camera) {
    if (!closed) terrainQueues.put(view, new SodiumTerrainQueue(tasks, camera));
  }

  public void queueShadow(LevelRenderer view, DeferredTaskList tasks, Vector3dc camera) {
    if (!closed && tasks != null) shadowQueues.put(view, new SodiumTerrainQueue(tasks, camera));
  }

  public void pruneQueues(LevelRenderer primary) {
    terrainQueues.keySet().removeIf(view -> !PortalSceneManager.isActiveView(view));
    shadowQueues.keySet().removeIf(view -> view != primary && !PortalSceneManager.isActiveView(view));
  }

  public void graphChanged() {
    listRevision++;
    for (var snapshot : snapshots) snapshot.invalidate();
    PortalSceneManager.invalidateTerrainPreparation();
  }

  public void listsChanged() { listRevision++; }

  public boolean sectionChanged(int x, int y, int z, int changes, boolean work) {
    var pendingView = pending == null ? null : pending.view;
    var pendingSnapshot = pending == null ? null : pending.snapshot;
    boolean graph = (changes & SectionInfoChange.GRAPH) != 0;
    boolean changedLists = graph || (changes & SectionInfoChange.RENDER_LIST) != 0;
    boolean primary = primaryFootprint == null || primaryFootprint.contains(x, y, z)
        || primarySnapshot != null && primarySnapshot.footprint().contains(x, y, z)
        || pendingView == null && pendingSnapshot != null && pendingSnapshot.footprint().contains(x, y, z);
    if (graph || work) {
      for (var snapshot : snapshots) {
        if (!snapshot.footprint().contains(x, y, z)) continue;
        if (graph) snapshot.invalidate();
        else snapshot.invalidateWork();
      }
      if (pendingView != null && pendingSnapshot != null && pendingSnapshot.footprint().contains(x, y, z))
        PortalSceneManager.invalidateTerrainPreparation(pendingView);
    }
    for (var entry : views.entrySet()) {
      var snapshot = entry.getValue().snapshot;
      if (snapshot != null && snapshot.footprint().contains(x, y, z)) {
        if (graph || work) PortalSceneManager.invalidateTerrainPreparation(entry.getKey());
        if (changedLists) lists.remove(entry.getKey());
      }
    }
    lists.entrySet().removeIf(entry -> {
      if (!entry.getValue().footprint.contains(x, y, z)) return false;
      if (graph || work) PortalSceneManager.invalidateTerrainPreparation(entry.getKey());
      return changedLists;
    });
    return primary;
  }

  public void stop() {
    closed = true;
    requests.clear();
    if (pending != null) pending.discarded = true;
  }

  public void clear() {
    lists.clear();
    views.clear();
    returnTrees.clear();
    snapshots.clear();
    terrainQueues.clear();
    shadowQueues.clear();
    pending = null;
    parkedPrimary = null;
    primarySnapshot = null;
    primaryFootprint = null;
    primaryDemand = new long[0];
  }

  public void retainDestinations(ClientLevel level, Viewport viewport, float distance,
      SectionTree wide, boolean shadow) {
    if (!shadow) primaryFootprint = SodiumCullRequest.Footprint.of(viewport, distance);
    var retained = new HashSet<Long>();
    for (var link : PortalTopology.links(level)) {
      var portal = link.entrance();
      if (!portal.endpoint().dimension().equals(level.dimension())) continue;
      long id = portal.identity();
      retained.add(id);
      var position = portal.endpoint().center();
      var destination = new Viewport(null, new Vector3d(position.x, position.y, position.z));
      if (wide != null && wide.isValidFor(destination, 0)) returnTrees.put(id, wide);
      else {
        var previous = returnTrees.get(id);
        if (previous != null && !previous.isValidFor(destination, 0)) returnTrees.remove(id);
      }
    }
    returnTrees.keySet().retainAll(retained);
  }

  public SectionTree reusableTree(Viewport viewport, float distance) {
    for (var tree : returnTrees.values()) if (tree.isValidFor(viewport, distance)) return tree;
    for (var view : views.values()) if (view.tree.isValidFor(viewport, distance)) return view.tree;
    return null;
  }

  public void patchRetained(RenderSection section) {
    // Like Sodium's current tree, retained trees admit newly published geometry immediately.
    for (var tree : returnTrees.values())
      tree.patchMarkPresent(section.getChunkX(), section.getChunkY(), section.getChunkZ());
    for (var view : views.values())
      view.tree.patchMarkPresent(section.getChunkX(), section.getChunkY(), section.getChunkZ());
  }

  public boolean completeView(LevelRenderer renderer, CameraRenderState camera, boolean primary,
      SectionTree primaryTree, float distance, SectionStorage sections, RenderRegionManager regions) {
    if (camera.cullFrustum == null) return false;
    var viewport = ((ViewportProvider) camera.cullFrustum).sodium$createViewport();
    if (!camera.smartCull) return unculledComplete(viewport, camera, regions);
    var visibility = primary ? null : views.get(renderer);
    var tree = primary ? primaryTree : visibility == null ? null : visibility.tree;
    var snapshot = primary ? primarySnapshot : visibility == null ? null : visibility.snapshot;
    if (tree == null || snapshot == null || !snapshot.visibilityFresh() || !snapshot.workFresh()
        || !primary && !Objects.equals(visibility.plane, SodiumCullRequest.plane(viewport))
        || !tree.isValidFor(viewport, distance) || !PortalTerrainDemand.resident(camera)) return false;
    var demand = primary ? primaryDemand : visibility.demand;
    for (long node : demand) {
      int x = SectionPos.x(node), y = SectionPos.y(node), z = SectionPos.z(node);
      if (viewport.isBoxVisible(x * 16 + 8, y * 16 + 8, z * 16 + 8)
          && !published(sections.getConsistent(node))) return false;
    }
    boolean[] complete = {true};
    tree.tree.traverse((x, y, z) -> {
      if (complete[0]) complete[0] = published(sections.getConsistent(SectionPos.asLong(x, y, z)));
    }, viewport, distance);
    return complete[0];
  }

  private static boolean unculledComplete(Viewport viewport, CameraRenderState camera, RenderRegionManager regions) {
    if (!PortalTerrainDemand.resident(camera)) return false;
    int radius = Minecraft.getInstance().options.getEffectiveRenderDistance();
    var center = viewport.getChunkCoord();
    for (var region : regions.getLoadedRegions()) {
      for (var section : ((RenderRegionAccessor) region).portalix$sections()) {
        if (section != null && Math.abs(section.getChunkY() - center.y()) <= radius
            && ChunkTrackingView.isInViewDistance(center.x(), center.z(), radius, section.getChunkX(), section.getChunkZ())
            && viewport.isBoxVisible(section.getCenterX(), section.getCenterY(), section.getCenterZ())
            && !published(section)) return false;
      }
    }
    return true;
  }

  private static boolean published(RenderSection section) {
    if (section == null || section.isDisposed() || !section.isBuilt() || section.getPendingUpdate() != 0) return false;
    var pending = (RenderSectionAccessor) section;
    return pending.portalix$runningJobs().isEmpty() && pending.portalix$pendingBuildOutput() == null
        && pending.portalix$pendingSortOutput() == null;
  }
}
