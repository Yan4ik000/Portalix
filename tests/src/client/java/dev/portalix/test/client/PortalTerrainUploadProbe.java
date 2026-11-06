package dev.portalix.test.client;

import dev.portalix.portal.PortalLink;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;

/** Waits for a portal image rendered after the watched terrain updates reached the renderer. */
public final class PortalTerrainUploadProbe implements AutoCloseable {
  private record Upload(long count, long committedNanos) {}
  private record Render(long extractedNanos, long completedNanos) {}
  private static volatile PortalTerrainUploadProbe active;
  private final Map<Long, Upload> uploads = new LinkedHashMap<>();
  private final Map<List<PortalLink>, Render> renderedViews = new LinkedHashMap<>();

  PortalTerrainUploadProbe(AABB bounds) {
    synchronized (PortalTerrainUploadProbe.class) {
      if (active != null) throw new IllegalStateException("A terrain upload observation is already active");
      for (BlockPos position : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
          BlockPos.containing(bounds.maxX - .01, bounds.maxY - .01, bounds.maxZ - .01)))
        uploads.put(SectionPos.asLong(position), new Upload(0, 0));
      active = this;
    }
  }

  public static synchronized void uploaded(long section) {
    if (active != null && active.uploads.containsKey(section))
      active.uploads.compute(section, (ignored, previous) ->
          new Upload(previous.count + 1, FrameCapture.monotonicNanos()));
  }

  public static synchronized long extractionStarted() {
    return active == null ? 0 : FrameCapture.monotonicNanos();
  }

  public static synchronized void rendered(List<PortalLink> path, long extractedNanos) {
    if (active != null && extractedNanos > 0)
      active.renderedViews.put(List.copyOf(path), new Render(extractedNanos, FrameCapture.monotonicNanos()));
  }

  boolean complete(PortalLink link) {
    synchronized (PortalTerrainUploadProbe.class) {
      Render render = renderedViews.get(List.of(link));
      return render != null && render.completedNanos >= render.extractedNanos
          && uploads.values().stream().allMatch(upload -> upload.count > 0 && render.extractedNanos > upload.committedNanos);
    }
  }

  List<Map<String, Object>> diagnostics() {
    synchronized (PortalTerrainUploadProbe.class) {
      var views = renderedViews.entrySet().stream().map(entry -> Map.of("path", entry.getKey().toString(),
          "extractedNanos", entry.getValue().extractedNanos, "renderCompletedNanos", entry.getValue().completedNanos)).toList();
      return uploads.entrySet().stream().map(entry -> Map.<String, Object>of(
          "section", SectionPos.of(entry.getKey()).toString(),
          "completedUploads", entry.getValue().count,
          "lastCommitNanos", entry.getValue().committedNanos,
          "portalRenders", views)).toList();
    }
  }

  @Override public void close() {
    synchronized (PortalTerrainUploadProbe.class) {
      if (active == this) active = null;
    }
  }
}
