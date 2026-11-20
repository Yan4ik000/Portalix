package dev.portalix.client.portal.terrain;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SharedOcclusionExecutor {
  private static final ExecutorService WORKERS = Executors.newWorkStealingPool();

  private SharedOcclusionExecutor() {}

  public static Executor executor() {
    return WORKERS;
  }

  public static void close() {
    WORKERS.shutdown();
  }
}
