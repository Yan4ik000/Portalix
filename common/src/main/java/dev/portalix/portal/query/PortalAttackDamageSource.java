package dev.portalix.portal.query;

import dev.portalix.portal.camera.PortalLookAngles;
import dev.portalix.portal.traversal.PortalPassagePose;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class PortalAttackDamageSource extends DamageSource {
  private final Vec3 position;
  private final float yaw;
  private final PortalInteractionPath.Route route;

  private PortalAttackDamageSource(
      DamageSource original, Vec3 position, float yaw, PortalInteractionPath.Route route) {
    super(original.typeHolder(), original.getDirectEntity(), original.getEntity());
    this.position = position;
    this.yaw = yaw;
    this.route = route;
  }

  public static DamageSource throughPortal(
      Player player, Entity target, ItemStack weapon, DamageSource original) {
    var range = player.getAttackRangeWith(weapon);
    double maximum = range.effectiveMaxRange(player) + range.hitboxMargin() + 3;
    var selected =
        PortalInteractionPath.closest(player, PortalPassagePose.interactionBounds(target), maximum);
    if (selected.isEmpty()) return original;
    var route = selected.orElseThrow();
    if (route.links().isEmpty()) return original;
    float yaw = player.getYRot();
    float pitch = 0;
    for (var link : route.links()) {
      var angles = PortalLookAngles.transform(link.transform(), yaw, pitch);
      yaw = angles.yaw();
      pitch = angles.pitch();
    }
    Vec3 origin = original.getSourcePosition();
    Vec3 position = route.point(origin == null ? player.position() : origin);
    return new PortalAttackDamageSource(original, position, yaw, route);
  }

  public float yaw() {
    return yaw;
  }

  public double distanceToSqr(Entity target) {
    if (!route.visible(PortalPassagePose.interactionBounds(target)))
      return Double.POSITIVE_INFINITY;
    return position.distanceToSqr(target.position());
  }

  @Override
  public Vec3 getSourcePosition() {
    return position;
  }

  @Override
  public Vec3 sourcePositionRaw() {
    return position;
  }
}
