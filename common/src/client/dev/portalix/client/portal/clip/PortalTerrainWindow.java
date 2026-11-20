package dev.portalix.client.portal.clip;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkTrackingView;
import org.joml.FrustumIntersection;

/** The same section footprint clips terrain, feature bounds and points, even in overlapping views. */
public record PortalTerrainWindow(SectionPos center, int radius) {
  public boolean containsPoint(double x, double y, double z) {
    return containsOffset(SectionPos.posToSectionCoord(x) - center.x(),
        SectionPos.posToSectionCoord(y) - center.y(), SectionPos.posToSectionCoord(z) - center.z());
  }

  public int classifyBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    return classifyOffsets(SectionPos.posToSectionCoord(minX) - center.x(),
        SectionPos.posToSectionCoord(minY) - center.y(), SectionPos.posToSectionCoord(minZ) - center.z(),
        lastSection(minX, maxX) - center.x(), lastSection(minY, maxY) - center.y(), lastSection(minZ, maxZ) - center.z());
  }

  private static int lastSection(double min, double max) {
    return SectionPos.posToSectionCoord(max > min ? Math.nextDown(max) : min);
  }

  public int classifyOffsets(int x0, int y0, int z0, int x1, int y1, int z1) {
    if (!containsOffset(Math.clamp(0, x0, x1), Math.clamp(0, y0, y1), Math.clamp(0, z0, z1)))
      return FrustumIntersection.OUTSIDE;
    return containsOffset(Math.max(Math.abs(x0), Math.abs(x1)), Math.max(Math.abs(y0), Math.abs(y1)),
        Math.max(Math.abs(z0), Math.abs(z1))) ? FrustumIntersection.INSIDE : FrustumIntersection.INTERSECT;
  }

  public boolean containsOffset(int x, int y, int z) {
    return Math.abs(y) <= radius && ChunkTrackingView.isInViewDistance(0, 0, radius, x, z);
  }
}
