package dev.portalix.portal.camera;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

public final class PortalCameraDistance {
  public static final int MAX_MODDED_LOADING_DISTANCE = 128;

  private PortalCameraDistance() {}

  public static float vanilla(LivingEntity cameraEntity) {
    float distance = scaledDistance(cameraEntity);
    if (cameraEntity.getVehicle() instanceof LivingEntity mount)
      distance = Math.max(distance, scaledDistance(mount));
    return distance;
  }

  public static float limit(LivingEntity cameraEntity) {
    float scale = cameraEntity.getScale();
    if (cameraEntity.getVehicle() instanceof LivingEntity mount)
      scale = Math.max(scale, mount.getScale());
    return (float) ((RangedAttribute) Attributes.CAMERA_DISTANCE.value()).getMaxValue() * scale;
  }

  private static float scaledDistance(LivingEntity entity) {
    return (float) entity.getAttributeValue(Attributes.CAMERA_DISTANCE) * entity.getScale();
  }
}
