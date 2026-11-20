package dev.portalix.client.portal.clip;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;

public interface PortalFrustumState {
  Matrix4fc portalix$cullingMatrix();

  boolean portalix$isAperture();

  void portalix$aperture(Vector3fc direction, PortalCullPlane exit);

  PortalCullPlane portalix$exitPlane();

  void portalix$exitPlane(PortalCullPlane plane);

  Vec3 portalix$preparationCell();

  void portalix$preparationCell(Vec3 origin);

  PortalTerrainWindow portalix$terrainWindow();

  void portalix$terrainWindow(PortalTerrainWindow window);
}
