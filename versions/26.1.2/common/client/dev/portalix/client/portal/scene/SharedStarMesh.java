package dev.portalix.client.portal.scene;

import com.mojang.blaze3d.buffers.GpuBuffer;
import java.util.function.Supplier;

public final class SharedStarMesh {
  private static Mesh mesh;
  private static int references;

  private SharedStarMesh() {}

  public record Mesh(GpuBuffer buffer, int indexCount) {}

  public static Mesh acquire(Supplier<Mesh> factory) {
    if (mesh == null) mesh = factory.get();
    references++;
    return mesh;
  }

  public static void release(GpuBuffer buffer, Runnable close) {
    if (mesh == null || mesh.buffer() != buffer)
      throw new IllegalStateException("Unowned portal sky stars");
    if (--references == 0) {
      close.run();
      mesh = null;
    }
  }
}
