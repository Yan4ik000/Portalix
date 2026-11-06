package dev.portalix.client.portal.camera;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public interface CameraOrientationAccess {
  void portalix$setPose(Vec3 position, Quaternionf rotation);

  float portalix$eyeHeight(float partialTick);

  float portalix$takeEyeOffset(float partialTick);
}
