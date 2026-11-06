package dev.portalix.client.portal.terrain;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.UberGpuBuffer;
import com.mojang.blaze3d.vertex.MeshData;
import dev.portalix.client.mixin.terrain.SectionRenderDispatcherAccessor;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSectionBufferSlice;
import net.minecraft.client.renderer.chunk.TranslucencyPointOfView;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

public final class PortalTranslucentIndices implements AutoCloseable {
  private final Map<CompiledSectionMesh, Entry> entries = new IdentityHashMap<>();
  private final Set<Entry> pending = new LinkedHashSet<>();
  private UberGpuBuffer<Entry> buffer;
  private boolean releasedAllocations;

  public void release(CompiledSectionMesh mesh) {
    var entry = entries.remove(mesh);
    if (entry == null) return;
    pending.remove(entry);
    entry.close();
    if (buffer != null) {
      buffer.removeAllocation(entry);
      releasedAllocations = true;
    }
  }

  public void reclaim(SectionRenderDispatcher dispatcher) {
    if (!releasedAllocations) return;
    dispatcher.lock();
    try {
      var device = RenderSystem.getDevice();
      var staging = ((SectionRenderDispatcherAccessor) dispatcher).portalix$stagingBuffer();
      try (var uploader = staging.startUploading(device.createCommandEncoder())) {
        releasedAllocations = buffer.uploadStagedAllocations(device, uploader);
      }
    } finally {
      dispatcher.unlock();
    }
  }

  public void schedule(
      RenderSection section, TranslucencyPointOfView pointOfView, Vec3 camera,
      boolean blockChanged, boolean nearby) {
    if (!(section.getSectionMesh() instanceof CompiledSectionMesh mesh)) return;
    var state = mesh.getTransparencyState();
    if (state == null || mesh.isEmpty(ChunkSectionLayer.TRANSLUCENT)) return;
    Entry entry = entries.computeIfAbsent(mesh,
        ignored -> new Entry(state, section.getRenderOrigin(), Util.backgroundExecutor()));
    pointOfView.set(camera, section.getSectionNode());
    entry.request(camera, blockChanged && (pointOfView.isAxisAligned() || nearby)
        || !pointOfView.equals(entry.pointOfView));
    if (entry.pending != null) pending.add(entry);
  }

  public void prepare(SectionRenderDispatcher dispatcher) {
    if (pending.isEmpty()) return;
    dispatcher.lock();
    try {
      var device = RenderSystem.getDevice();
      var staging = ((SectionRenderDispatcherAccessor) dispatcher).portalix$stagingBuffer();
      if (buffer == null)
        buffer = new UberGpuBuffer<>("Portal translucent indices",
            GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST, 32 * 1024 * 1024, 4, staging);
      boolean staged = false;
      var iterator = pending.iterator();
      while (iterator.hasNext()) {
        var entry = iterator.next();
        if (!entry.pending.isDone()) continue;
        var sorted = entry.pending.join();
        if (sorted != entry.sorted && sorted.indices().length != 0) {
          byte[] bytes = sorted.indices();
          var data = MemoryUtil.memAlloc(bytes.length);
          try {
            data.put(bytes).flip();
            if (!buffer.addAllocation(entry, this::uploaded, data)) break;
            entry.staged = sorted;
            staged = true;
          } finally {
            MemoryUtil.memFree(data);
          }
        }
        entry.finish(sorted);
        if (entry.pending == null) iterator.remove();
      }
      if (staged)
        try (var uploader = staging.startUploading(device.createCommandEncoder())) {
          buffer.uploadStagedAllocations(device, uploader);
        }
    } finally {
      dispatcher.unlock();
    }
  }

  private void uploaded(Entry entry) {
    var allocation = buffer.getAllocation(entry);
    entry.indices = new GpuBufferSlice(buffer.getGpuBuffer(allocation),
        allocation.getOffsetFromHeap(), allocation.getSize());
    entry.sorted = entry.staged;
    entry.staged = null;
  }

  public RenderSectionBufferSlice slice(
      CompiledSectionMesh mesh, RenderSectionBufferSlice original) {
    Entry entry = entries.get(mesh);
    return entry == null || entry.indices == null
        ? original
        : new RenderSectionBufferSlice(
            original.vertexBuffer(), original.vertexBufferOffset(),
            entry.indices.buffer(), entry.indices.offset());
  }

  @Override
  public void close() {
    entries.values().forEach(Entry::close);
    entries.clear();
    pending.clear();
    if (buffer != null) {
      buffer.close();
      buffer = null;
    }
    releasedAllocations = false;
  }

  static final class Entry {
    private final MeshData.SortState state;
    private final BlockPos origin;
    private final Executor executor;
    private Vec3 camera, requestedCamera;
    TranslucencyPointOfView pointOfView;
    CompletableFuture<PortalDistanceSort.Result> pending;
    GpuBufferSlice indices;
    PortalDistanceSort.Result sorted, staged;

    Entry(MeshData.SortState state, BlockPos origin, Executor executor) {
      this.state = state;
      this.origin = origin.immutable();
      this.executor = executor;
    }

    void request(Vec3 nextCamera, boolean required) {
      requestedCamera = nextCamera;
      if (pending == null && required) start(sorted);
    }

    private void start(PortalDistanceSort.Result previous) {
      camera = requestedCamera;
      if (pointOfView == null) pointOfView = new TranslucencyPointOfView();
      pointOfView.set(camera, SectionPos.asLong(origin));
      var relative = new Vector3f((float) (camera.x - origin.getX()),
          (float) (camera.y - origin.getY()), (float) (camera.z - origin.getZ()));
      pending = CompletableFuture.supplyAsync(
          () -> PortalDistanceSort.sort(state, relative, previous), executor);
    }

    void finish(PortalDistanceSort.Result result) {
      pending = null;
      if (!camera.equals(requestedCamera)) start(result);
    }

    void close() {
      if (pending != null) pending.cancel(false);
      pending = null;
      sorted = staged = null;
      indices = null;
      camera = requestedCamera = null;
    }
  }
}
