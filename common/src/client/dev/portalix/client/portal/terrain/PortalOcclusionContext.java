package dev.portalix.client.portal.terrain;

import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;

public final class PortalOcclusionContext {
  private static final ScopedValue<Snapshot> ACTIVE = ScopedValue.newInstance();

  private PortalOcclusionContext() {}

  public record Snapshot(Vec3 origin, SectionPos center, int radius, boolean smartCull) {
    public Snapshot(Vec3 origin, int radius, boolean smartCull) {
      this(origin, SectionPos.of(origin), radius, smartCull);
    }
  }

  public interface SnapshotAccess {
    Snapshot portalix$occlusionSnapshot();
  }

  public static Snapshot current() {
    return ACTIVE.isBound() ? ACTIVE.get() : null;
  }

  public static void run(Snapshot snapshot, Runnable task) {
    ScopedValue.where(ACTIVE, snapshot).run(task);
  }

  public static SectionPos cameraSection(SectionPos ordinary) {
    return ACTIVE.isBound() ? ACTIVE.get().center() : ordinary;
  }

  public static int radius(int ordinary) {
    return ACTIVE.isBound() ? ACTIVE.get().radius() : ordinary;
  }
}
