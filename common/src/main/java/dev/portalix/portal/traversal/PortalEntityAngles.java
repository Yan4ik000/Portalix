package dev.portalix.portal.traversal;

import dev.portalix.portal.camera.PortalLookAngles;
import dev.portalix.portal.geometry.PortalTransform;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;

public final class PortalEntityAngles {
  private PortalEntityAngles() {}

  public static PortalLookAngles transform(
      Entity entity, PortalTransform transform, float yaw, float pitch) {
    if (entity instanceof AbstractHurtingProjectile || entity instanceof ShulkerBullet) {
      var angles = PortalLookAngles.transform(transform, yaw - 180, pitch);
      return new PortalLookAngles(angles.yaw() + 180, angles.pitch());
    }
    if (entity instanceof Projectile) {
      var angles = PortalLookAngles.transform(transform, -yaw, -pitch);
      return new PortalLookAngles(-angles.yaw(), -angles.pitch());
    }
    return PortalLookAngles.transform(transform, yaw, pitch);
  }
}
