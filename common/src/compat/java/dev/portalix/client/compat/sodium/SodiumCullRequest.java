package dev.portalix.client.compat.sodium;

import dev.portalix.client.mixin.compat.sodium.ViewportAccessor;
import dev.portalix.client.portal.clip.PortalCullPlane;
import dev.portalix.client.portal.terrain.PortalOcclusionOpenings;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.FogParameters;

public record SodiumCullRequest(
    Viewport viewport, FogParameters fog, boolean culling, int frame, PortalCullPlane plane) {
  public SodiumCullRequest(Viewport viewport, FogParameters fog, boolean culling, int frame) {
    this(viewport, fog, culling, frame, plane(viewport));
  }

  public static PortalCullPlane plane(Viewport viewport) {
    if (!(((ViewportAccessor) (Object) viewport).portalix$frustum()
        instanceof SodiumPortalFrustum portal)) return null;
    if (PortalOcclusionOpenings.contains(viewport.getChunkCoord().asLong())) return null;
    return portal.worldExit();
  }

  public record Footprint(int x, int y, int z, int radius) {
    public static Footprint of(Viewport viewport, float distance) {
      var center = viewport.getChunkCoord();
      // Includes the padded section test, neighbor connectivity and the WIDE search halo.
      return new Footprint(center.getX(), center.getY(), center.getZ(),
          (int) Math.ceil(distance / 16.0) + 2);
    }

    public boolean contains(int sectionX, int sectionY, int sectionZ) {
      return Math.abs((long) sectionX - x) <= radius
          && Math.abs((long) sectionY - y) <= radius
          && Math.abs((long) sectionZ - z) <= radius;
    }
  }

  public static final class Snapshot {
    public static final ScopedValue<Snapshot> ACTIVE = ScopedValue.newInstance();

    private final Footprint footprint;
    private boolean visibilityFresh = true;
    private boolean workFresh = true;

    public Snapshot(Footprint footprint) {
      this.footprint = footprint;
    }

    public Footprint footprint() {
      return footprint;
    }

    public boolean visibilityFresh() {
      return visibilityFresh;
    }

    public boolean workFresh() {
      return workFresh;
    }

    public void invalidate() {
      visibilityFresh = false;
      workFresh = false;
    }

    public void invalidateWork() {
      workFresh = false;
    }
  }

  public interface SnapshotAccess {
    Snapshot portalix$cullSnapshot();
  }
}
