package dev.portalix.client.portal.clip;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

public interface PortalFrustumState {
  boolean portalix$isAperture();

  void portalix$aperture(Vector3fc direction, PortalCullPlane exit);

  PortalCullPlane portalix$exitPlane();

  void portalix$exitPlane(PortalCullPlane plane);

  Vec3 portalix$preparationCell();

  void portalix$preparationCell(Vec3 origin);
}
