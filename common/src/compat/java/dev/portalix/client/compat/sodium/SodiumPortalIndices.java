package dev.portalix.client.compat.sodium;

import dev.portalix.client.mixin.compat.sodium.RenderRegionAccessor;
import dev.portalix.client.mixin.compat.sodium.ChunkRenderListAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.caffeinemc.mods.sodium.client.gpu.arena.BufferSegment;
import net.caffeinemc.mods.sodium.client.gpu.arena.PendingUpload;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.DynamicData;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.util.NativeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class SodiumPortalIndices {
  public static final int OWNER = Integer.MAX_VALUE;
  private static final Map<LevelRenderer, View> VIEWS = new IdentityHashMap<>();
  private static long revision;

  private SodiumPortalIndices() {}

  public static void prepare(ChunkRenderListIterable lists, CameraTransform transform) {
    var view = VIEWS.computeIfAbsent(Minecraft.getInstance().levelRenderer, ignored -> new View());
    var entries = view.entries;
    var camera = new Vec3(transform.x, transform.y, transform.z);
    boolean unchanged = camera.equals(view.camera) && view.revision == revision && !view.pending;
    view.camera = camera;
    view.revision = revision;
    view.pending = false;
    var regions = lists.iterator(true);
    var uploads = new ArrayList<Upload>();
    var updates = new ArrayList<IndexUpdate>();
    var staging = new TransientChunkStaging();
    while (regions.hasNext()) {
      var list = regions.next();
      var region = list.getRegion();
      if (region.getStorage(DefaultTerrainRenderPasses.TRANSLUCENT) == null
          || unchanged && region.getCachedBatch(DefaultTerrainRenderPasses.TRANSLUCENT).isFilled)
        continue;
      var dynamicSections = ((SodiumDynamicSections) region).portalix$dynamicSections();
      var visibleSections = ((ChunkRenderListAccessor) list).portalix$geometry();
      var sections = ((RenderRegionAccessor) region).portalix$sections();
      try {
        for (int word = 0; word < dynamicSections.length; word++) {
          long remaining = dynamicSections[word] & visibleSections[word];
          while (remaining != 0) {
            int index = word * Long.SIZE + Long.numberOfTrailingZeros(remaining);
            remaining &= remaining - 1;
            var section = sections[index];
            var data = section.getTranslucentData();
            var entry = entries.get(section);
            if (entry != null
                && (entry.source != data
                    || entry.initial
                        != ((SodiumSortPlanesAccess) entry.source).portalix$initialSort())) {
              ((SodiumRegionViews) region)
                  .portalix$invalidateView(DefaultTerrainRenderPasses.TRANSLUCENT);
              entry.close();
              entries.remove(section);
              entry = null;
            }
            if (!(data instanceof DynamicData dynamic)) continue;
            if (entry == null) {
              entry = new Entry(dynamic);
              entry.seed(section, camera);
              entries.put(section, entry);
            }
            entry.prepare(section, camera, uploads, updates);
            view.pending |= entry.pending != null;
          }
        }
        if (!uploads.isEmpty()) {
          if (region
              .getResources()
              .getIndexAllocator()
              .upload(uploads.stream().map(Upload::upload))) region.onIndexBufferChange();
          for (var upload : uploads) upload.entry.allocation = upload.upload.getResult();
          ((SodiumRegionViews) region)
              .portalix$invalidateView(DefaultTerrainRenderPasses.TRANSLUCENT);
        }
        if (!updates.isEmpty()) {
          // New allocations can relocate the arena and existing segments before these copies.
          var buffer = region.getResources().getIndexAllocator().getBufferObject();
          for (var update : updates)
            staging.enqueueCopy(
                update.indices, buffer, update.entry.allocation.getOffset() * Integer.BYTES);
          staging.flush();
        }
      } finally {
        for (var upload : uploads) upload.data.free();
        uploads.clear();
        updates.clear();
      }
    }
  }

  public static long offset(RenderRegion region, int sectionIndex) {
    var dynamic = ((SodiumDynamicSections) region).portalix$dynamicSections();
    if ((dynamic[sectionIndex >>> 6] & (1L << (sectionIndex & 63))) == 0) return -1;
    var view = VIEWS.get(Minecraft.getInstance().levelRenderer);
    if (view == null) return -1;
    var section = ((RenderRegionAccessor) region).portalix$sections()[sectionIndex];
    var entry = view.entries.get(section);
    return entry == null || entry.allocation == null ? -1 : entry.allocation.getOffset();
  }

  public static void remove(RenderSection section) {
    if (!(section.getTranslucentData() instanceof DynamicData)) return;
    boolean removed = false;
    for (var view : VIEWS.values()) {
      var entry = view.entries.remove(section);
      if (entry != null) {
        entry.close();
        removed = true;
      }
    }
    if (removed) {
      geometryChanged();
      section.getRegion().clearCachedBatchFor(DefaultTerrainRenderPasses.TRANSLUCENT);
    }
  }

  public static void remove(RenderRegion region) {
    for (var section : ((RenderRegionAccessor) region).portalix$sections())
      if (section != null) remove(section);
  }

  public static void close(LevelRenderer renderer) {
    var view = VIEWS.remove(renderer);
    if (view != null)
      view.entries.forEach(
          (section, entry) -> {
            section.getRegion().clearCachedBatchFor(DefaultTerrainRenderPasses.TRANSLUCENT);
            entry.close();
          });
  }

  public static void retarget(LevelRenderer renderer) {
    var view = VIEWS.get(renderer);
    if (view == null) return;
    for (var entry : view.entries.values()) {
      if (entry.pending == null) continue;
      entry.pending.cancel(false);
      entry.pending = null;
      entry.task = null;
      entry.camera = null;
      entry.relativeCamera = null;
    }
    view.camera = null;
    view.pending = false;
  }

  public static void exchangePrimary(LevelRenderer renderer) {
    var primary = Minecraft.getInstance().levelRenderer;
    var source = VIEWS.remove(primary);
    var destination = VIEWS.remove(renderer);
    if (source != null) {
      source.bind(renderer);
      VIEWS.put(renderer, source);
    }
    if (destination != null) {
      destination.bind(primary);
      VIEWS.put(primary, destination);
    }
  }

  public static void geometryChanged() {
    revision++;
  }

  private static final class View {
    final Map<RenderSection, Entry> entries = new IdentityHashMap<>();
    Vec3 camera;
    long revision = Long.MIN_VALUE;
    boolean pending;

    void bind(LevelRenderer renderer) {
      for (var entry : entries.values())
        if (entry.task != null) entry.task.view = renderer;
    }
  }

  private static final class Entry {
    final DynamicData source;
    final SodiumSortPlanes planes;
    final SodiumInitialSort initial;
    Vec3 camera;
    Vector3fc relativeCamera;
    CompletableFuture<SodiumIndexSortResult> pending;
    SodiumViewSortTask task;
    BufferSegment allocation;
    SortOrder sorted;

    Entry(DynamicData source) {
      this.source = source;
      planes = ((SodiumSortPlanesAccess) source).portalix$sortPlanes();
      initial = ((SodiumSortPlanesAccess) source).portalix$initialSort();
    }

    void seed(RenderSection section, Vec3 position) {
      var relative = relative(section, position);
      if (initial != null) {
        var result = initial.result();
        var origin = relative(section, initial.camera());
        if (position.equals(initial.camera())
            || (result.topology() == null || !result.topology().distance())
                && planes != null
                && !planes.crossed(origin, relative)) {
          camera = initial.camera();
          relativeCamera = origin;
          sorted = new SortOrder(initial.camera(), origin, result);
          return;
        }
      }
      for (var view : VIEWS.values()) {
        var previous = view.entries.get(section);
        if (previous == null || previous.source != source || previous.sorted == null) continue;
        var order = previous.sorted;
        if (position.equals(order.camera())
            || order.planeOrdered()
                && planes != null
                && !planes.crossed(order.relativeCamera(), relative)) {
          camera = order.camera();
          relativeCamera = order.relativeCamera();
          pending = CompletableFuture.completedFuture(order.result());
          return;
        }
      }
    }

    private static Vector3fc relative(RenderSection section, Vec3 position) {
      return new Vector3f(
          (float) (position.x - section.getOriginX()),
          (float) (position.y - section.getOriginY()),
          (float) (position.z - section.getOriginZ()));
    }

    void prepare(
        RenderSection section,
        Vec3 position,
        ArrayList<Upload> uploads,
        ArrayList<IndexUpdate> updates) {
      if (task != null && task.cancelled()) pending.cancel(false);
      if (pending != null && pending.isCancelled()) {
        pending = null;
        task = null;
        camera = null;
        relativeCamera = null;
      }
      if (pending != null && pending.isDone()) {
        var result = pending.join();
        byte[] bytes = result.indices();
        pending = null;
        task = null;
        if (sorted == null || !Arrays.equals(bytes, sorted.result().indices())) {
          if (allocation == null) {
            var data = new NativeBuffer(bytes.length);
            data.getDirectBuffer().put(bytes);
            uploads.add(new Upload(this, data, new PendingUpload(data, OWNER)));
          } else {
            updates.add(new IndexUpdate(this, bytes));
          }
        }
        sorted = new SortOrder(camera, relativeCamera, result);
      }
      if (pending == null && !position.equals(camera)) {
        camera = position;
        float x = (float) (position.x - section.getOriginX());
        float y = (float) (position.y - section.getOriginY());
        float z = (float) (position.z - section.getOriginZ());
        boolean crossed =
            relativeCamera == null || planes == null || planes.crossed(relativeCamera, x, y, z);
        if ((sorted == null || sorted.planeOrdered()) && !crossed) return;
        var absolute = new Vector3d(position.x, position.y, position.z);
        var previousTopology = sorted == null ? null : sorted.result().topology();
        task =
            new SodiumViewSortTask(
                section,
                Minecraft.getInstance().levelRenderer,
                absolute,
                source,
                previousTopology,
                crossed);
        relativeCamera = task.getRelativeCameraPos();
        pending = task.result;
        ((SodiumPrimaryView) SodiumWorldRenderer.instance()).portalix$queueSort(task);
      }
    }

    void close() {
      if (pending != null) pending.cancel(false);
      if (allocation != null) allocation.delete();
    }
  }

  private record SortOrder(Vec3 camera, Vector3fc relativeCamera, SodiumIndexSortResult result) {
    boolean planeOrdered() {
      return result.topology() == null || !result.topology().distance();
    }
  }

  private record Upload(Entry entry, NativeBuffer data, PendingUpload upload) {}

  private record IndexUpdate(Entry entry, byte[] indices) {}
}
