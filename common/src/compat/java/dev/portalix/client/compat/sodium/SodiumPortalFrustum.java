package dev.portalix.client.compat.sodium;

import dev.portalix.client.portal.clip.PortalCullPlane;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.render.viewport.frustum.Frustum;
import org.joml.FrustumIntersection;

public record SodiumPortalFrustum(
    Frustum delegate, PortalCullPlane exit, PortalCullPlane worldExit) implements Frustum {
  @Override
  public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    return (exit == null || exit.intersects(minX, minY, minZ, maxX, maxY, maxZ))
        && delegate.testAab(minX, minY, minZ, maxX, maxY, maxZ);
  }

  @Override
  public int intersectAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    if (exit == null) return delegate.intersectAab(minX, minY, minZ, maxX, maxY, maxZ);
    if (!exit.intersects(minX, minY, minZ, maxX, maxY, maxZ)) return FrustumIntersection.OUTSIDE;
    int result = delegate.intersectAab(minX, minY, minZ, maxX, maxY, maxZ);
    return result == FrustumIntersection.INSIDE
            && !exit.contains(minX, minY, minZ, maxX, maxY, maxZ)
        ? FrustumIntersection.INTERSECT
        : result;
  }

  @Override
  public boolean testSection(float x, float y, float z) {
    return sectionIntersects(x, y, z, Viewport.CHUNK_SECTION_PADDED_RADIUS)
        && delegate.testSection(x, y, z);
  }

  @Override
  public boolean testSectionExpanded(float x, float y, float z, float expand) {
    return sectionIntersects(x, y, z, Viewport.CHUNK_SECTION_PADDED_RADIUS + expand)
        && delegate.testSectionExpanded(x, y, z, expand);
  }

  private boolean sectionIntersects(float x, float y, float z, float radius) {
    return exit == null
        || exit.intersects(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
  }
}
