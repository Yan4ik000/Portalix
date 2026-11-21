package dev.portalix.portal;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.geometry.PortalAperture;
import java.util.List;
import java.util.function.LongPredicate;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PortalViewLimits {
  public static final int MIN_PREVIEW_RADIUS = 4;
  public static final int DEFAULT_PREVIEW_RADIUS = 8;
  public static final int MAX_PREVIEW_RADIUS = 64;
  public static final int CORE_RADIUS = 1;
  public static final int ROUTES = 8;
  public static final int REGIONS = 4;
  public static final int DEPTH = 2;
  public static final int CAMERA_DEPTH = 64;

  public static boolean canCross(List<PortalLink> frame, PortalLink link) {
    return frame.size() < CAMERA_DEPTH || frame.getLast().inverse().equals(link);
  }

  public static int preview(int primary) {
    return Math.min(DEFAULT_PREVIEW_RADIUS, primary);
  }

  public static Vec3 origin(PortalEndpoint exit) {
    return exit.center().add(exit.normal().scale(PortalAperture.PLANE_EPSILON));
  }

  public static ChunkPos frontColumn(PortalEndpoint exit) {
    var point = origin(exit);
    return new ChunkPos(SectionPos.posToSectionCoord(point.x), SectionPos.posToSectionCoord(point.z));
  }

  public static boolean hasExitCore(PortalEndpoint exit, LongPredicate present) {
    return hasExitCore(frontColumn(exit), present);
  }

  public static boolean hasExitCore(ChunkPos center, LongPredicate present) {
    for (int x = center.x() - CORE_RADIUS; x <= center.x() + CORE_RADIUS; x++)
      for (int z = center.z() - CORE_RADIUS; z <= center.z() + CORE_RADIUS; z++)
        if (!present.test(ChunkPos.pack(x, z))) return false;
    return true;
  }

  public static boolean containsEntrance(Vec3 origin, PortalEndpoint portal, int radius) {
    var center = origin(portal);
    var extent = PortalAperture.halfExtents(portal);
    int section = SectionPos.posToSectionCoord(origin.y);
    int minY = SectionPos.sectionToBlockCoord(section - radius);
    int maxY = SectionPos.sectionToBlockCoord(section + radius + 1);
    if (center.y + extent.y < minY || center.y - extent.y >= maxY) return false;
    if (center.y >= minY && center.y < maxY && contains(origin, center, radius)) return true;

    int originX = SectionPos.posToSectionCoord(origin.x), originZ = SectionPos.posToSectionCoord(origin.z);
    int minX = SectionPos.posToSectionCoord(center.x - extent.x);
    int maxX = SectionPos.posToSectionCoord(center.x + extent.x);
    int minZ = SectionPos.posToSectionCoord(center.z - extent.z);
    int maxZ = SectionPos.posToSectionCoord(center.z + extent.z);
    var toPlane = portal.center().subtract(center);
    for (int x = minX; x <= maxX; x++)
      for (int z = minZ; z <= maxZ; z++) {
        if (!ChunkTrackingView.isInViewDistance(originX, originZ, radius, x, z)) continue;
        var column = new AABB(SectionPos.sectionToBlockCoord(x), minY, SectionPos.sectionToBlockCoord(z),
            SectionPos.sectionToBlockCoord(x + 1), maxY, SectionPos.sectionToBlockCoord(z + 1));
        if (PortalAperture.touches(portal, column.move(toPlane))) return true;
      }
    return false;
  }

  public static List<PortalLink> nearbyEntrances(Level level, Vec3 origin, double range) {
    // The topology index stores centers; the last visible column can contain only the aperture's edge.
    double reach = range + 16 + PortalAperture.MAX_APERTURE_EXTENT;
    return PortalTopology.entrances(level, origin.x - reach, origin.z - reach, origin.x + reach, origin.z + reach);
  }

  public static boolean contains(Vec3 center, Vec3 point, int radius) {
    return ChunkTrackingView.isInViewDistance(
        SectionPos.posToSectionCoord(center.x), SectionPos.posToSectionCoord(center.z),
        radius, SectionPos.posToSectionCoord(point.x), SectionPos.posToSectionCoord(point.z));
  }

  private PortalViewLimits() {}
}
