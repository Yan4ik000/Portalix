package dev.portalix.client.portal.terrain;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.TracingExecutor;
import net.minecraft.client.renderer.RenderBuffers;

public final class SharedTerrainExecutor {
  private static ExecutorService workers;
  private static TracingExecutor executor;

  private SharedTerrainExecutor() {}

  public static synchronized TracingExecutor executor(RenderBuffers buffers) {
    if (executor == null) {
      int parallelism =
          Math.min(
              Runtime.getRuntime().availableProcessors(),
              buffers.sectionBufferPool().getFreeBufferCount());
      workers =
          Executors.newFixedThreadPool(
              parallelism,
              Thread.ofPlatform()
                  .name("Portal terrain-", 0)
                  .priority(Thread.NORM_PRIORITY)
                  .factory());
      executor = new TracingExecutor(workers);
    }
    return executor;
  }

  public static void close() {
    if (workers != null) workers.shutdown();
  }
}
