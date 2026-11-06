package dev.portalix.client.portal.terrain;

import dev.portalix.client.portal.clip.PortalCullPlane;
import java.util.function.Supplier;
import net.minecraft.core.SectionPos;

public final class PortalOcclusionContext {
  private static final ScopedValue<PortalCullPlane> EXIT = ScopedValue.newInstance();
  private static final ScopedValue<SectionPos> CAMERA = ScopedValue.newInstance();

  private PortalOcclusionContext() {}

  public static <T> T call(PortalCullPlane exit, Supplier<T> task) {
    return exit == null ? task.get() : ScopedValue.where(EXIT, exit).call(task::get);
  }

  public static void run(PortalCullPlane exit, Runnable task) {
    if (exit == null) task.run();
    else ScopedValue.where(EXIT, exit).run(task);
  }

  public static void run(PortalCullPlane exit, SectionPos camera, Runnable task) {
    ScopedValue.where(CAMERA, camera).run(() -> run(exit, task));
  }

  public static SectionPos cameraSection(SectionPos ordinary) {
    return CAMERA.isBound() ? CAMERA.get() : ordinary;
  }

  public static boolean open(long section) {
    if (PortalOcclusionOpenings.contains(section)) return true;
    if (!EXIT.isBound()) return false;
    int x = SectionPos.x(section) * 16;
    int y = SectionPos.y(section) * 16;
    int z = SectionPos.z(section) * 16;
    return !EXIT.get().intersects(x, y, z, x + 16, y + 16, z + 16);
  }
}
