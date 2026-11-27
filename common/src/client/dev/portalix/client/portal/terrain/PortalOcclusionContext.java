package dev.portalix.client.portal.terrain;

import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;

public final class PortalOcclusionContext {
  private static final ScopedValue<Snapshot> ACTIVE = ScopedValue.newInstance();

  private PortalOcclusionContext() {}

  public record Snapshot(
      Vec3 origin, SectionPos center, int radius, boolean smartCull, boolean hasSeed) {
    public boolean matchesView(Vec3 origin, int radius) {
      return hasSeed && this.radius == radius && center.equals(SectionPos.of(origin))
          && !PortalTerrainColumns.current().isMissingAt(center);
    }
  }

  public static Snapshot capture(Vec3 origin, int radius, boolean smartCull) {
    var center = SectionPos.of(origin);
    return new Snapshot(origin, center, radius, smartCull,
        !PortalTerrainColumns.current().isMissingAt(center));
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
