package dev.portalix.client.compat.sodium;

import java.util.concurrent.CompletableFuture;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkUpdateTypes;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation.MeshTaskSizeEstimator;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJob;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderTask;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.DynamicData;
import net.caffeinemc.mods.sodium.client.util.task.CancellationToken;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Vector3dc;

public final class SodiumViewSortTask extends ChunkBuilderTask<SodiumViewSortTask.Output>
    implements Comparable<SodiumViewSortTask> {
  private final DynamicData source;
  private final SodiumTopoSorterAccess.State previousTopology;
  private final boolean crossedPlane;
  private final long queued = System.nanoTime();
  public LevelRenderer view;
  public final CompletableFuture<SodiumIndexSortResult> result = new CompletableFuture<>();
  public ChunkJob job;
  private double priority;

  public SodiumViewSortTask(
      RenderSection section,
      LevelRenderer view,
      Vector3dc camera,
      DynamicData source,
      SodiumTopoSorterAccess.State previousTopology,
      boolean crossedPlane) {
    super(section, 0, camera);
    this.view = view;
    this.source = source;
    this.previousTopology = previousTopology;
    this.crossedPlane = crossedPlane;
  }

  public void prioritize(float renderDistance) {
    double dx = section.getCenterX() - absoluteCameraPos.x();
    double dy = section.getCenterY() - absoluteCameraPos.y();
    double dz = section.getCenterZ() - absoluteCameraPos.z();
    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
    // Use Sodium's visible deferred-task priority, including its five-second aging interval.
    priority =
        ChunkUpdateTypes.getPriorityValue(ChunkUpdateTypes.SORT)
            + distance * (3.0 / renderDistance)
            + Math.max(distance, 50) * .012
            - 3;
  }

  public double priority(long now) {
    return priority - (now - queued) / 5_000_000_000.0;
  }

  @Override
  public int compareTo(SodiumViewSortTask other) {
    return Double.compare(priority - other.priority + (queued - other.queued) / 5_000_000_000.0, 0);
  }

  public boolean cancelled() {
    return result.isCancelled() || job != null && job.isCancelled();
  }

  @Override
  public Output execute(ChunkBuildContext context, CancellationToken cancellation) {
    if (cancellation.isCancelled() || result.isCancelled()) return null;
    var sorter = source.getSorter(false);
    try {
      if (sorter instanceof SodiumTopoSorterAccess topology)
        topology.portalix$state(
            previousTopology == null ? topology.portalix$state() : previousTopology,
            crossedPlane);
      sorter.writeIndexBuffer(this);
      var buffer = sorter.getIndexBuffer().getDirectBuffer();
      byte[] indices = new byte[buffer.remaining()];
      buffer.get(indices);
      return new Output(
          this,
          new SodiumIndexSortResult(
              indices,
              sorter instanceof SodiumTopoSorterAccess topology ? topology.portalix$state() : null));
    } finally {
      sorter.destroy();
    }
  }

  @Override
  public long estimateTaskSizeWith(MeshTaskSizeEstimator estimator) {
    return source.getIndexQuadCount() * 6L * Integer.BYTES;
  }

  public static final class Output extends BuilderTaskOutput {
    private final SodiumViewSortTask task;
    private final SodiumIndexSortResult indices;

    Output(SodiumViewSortTask task, SodiumIndexSortResult indices) {
      super(task.section, task.submitTime);
      this.task = task;
      this.indices = indices;
    }

    public void publish() {
      task.result.complete(indices);
    }

    @Override
    protected long calculateResultSize() {
      return indices.indices().length;
    }
  }
}
