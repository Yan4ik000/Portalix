package dev.portalix.client.portal.clip;

import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.joml.Matrix4fc;

public final class PortalFrustumPlanes {
  private PortalFrustumPlanes() {}

  public static FrustumIntersection set(FrustumIntersection intersection, Matrix4fc matrix) {
    // Point/AABB tests need half-space signs, not unit normals. A rounded infinite far plane has a zero normal.
    return intersection.set(matrix, false);
  }

  public static int intersectPreparationCell(
      FrustumIntersection intersection,
      Vec3 cell,
      double minX, double minY, double minZ,
      double maxX, double maxY, double maxZ) {
    // The relative box contains this world box as seen from every camera in the eight-block cell.
    return intersection.intersectAab(
        (float) (minX - (cell.x + 8)),
        (float) (minY - (cell.y + 8)),
        (float) (minZ - (cell.z + 8)),
        (float) (maxX - cell.x),
        (float) (maxY - cell.y),
        (float) (maxZ - cell.z));
  }
}
