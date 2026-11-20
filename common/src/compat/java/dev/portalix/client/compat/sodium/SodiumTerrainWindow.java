package dev.portalix.client.compat.sodium;

import dev.portalix.client.portal.clip.PortalTerrainWindow;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;

/** Classifies Sodium's section and octree bounds in the active viewport's relative coordinates. */
public final class SodiumTerrainWindow {
  private final PortalTerrainWindow window;
  private final float centerX, centerY, centerZ;

  public SodiumTerrainWindow(Vec3 origin, PortalTerrainWindow window) {
    this.window = window;
    centerX = (float) (window.center().x() * 16 + 8 - origin.x);
    centerY = (float) (window.center().y() * 16 + 8 - origin.y);
    centerZ = (float) (window.center().z() * 16 + 8 - origin.z);
  }

  public boolean section(float x, float y, float z) {
    return window.containsOffset(Math.round((x - centerX) / 16), Math.round((y - centerY) / 16),
        Math.round((z - centerZ) / 16));
  }

  public int classify(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    // Node bounds include a model overhang. Only the section centers inside the node are visited.
    int x0 = (int) Math.ceil((minX - centerX) / 16), x1 = (int) Math.floor((maxX - centerX) / 16);
    int y0 = (int) Math.ceil((minY - centerY) / 16), y1 = (int) Math.floor((maxY - centerY) / 16);
    int z0 = (int) Math.ceil((minZ - centerZ) / 16), z1 = (int) Math.floor((maxZ - centerZ) / 16);
    if (x0 > x1 || y0 > y1 || z0 > z1) return FrustumIntersection.INTERSECT;
    return window.classifyOffsets(x0, y0, z0, x1, y1, z1);
  }
}
