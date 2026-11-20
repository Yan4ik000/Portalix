package dev.portalix.portal.physics;

import dev.portalix.PortalixMod;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.traversal.PortalEntityMovement;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

public final class PortalEntityFunneling {
  public static final TagKey<EntityType<?>> PHYSICS_PROPS =
      TagKey.create(Registries.ENTITY_TYPE, PortalixMod.id("physics_props"));

  private PortalEntityFunneling() {}

  public static void tick(Entity entity) {
    if (!entity.typeHolder().is(PHYSICS_PROPS)
        || (entity.level().isClientSide() && !entity.isLocalInstanceAuthoritative())) return;
    if (PortalTopology.links(entity.level()).isEmpty()) return;
    var velocity = entity.getDeltaMovement();
    if (!PortalFunneling.eligible(velocity)) return;
    var center = entity.getBoundingBox().getCenter();
    PortalLink nearest = null;
    double distance = Double.POSITIVE_INFINITY;
    double radius = PortalFunneling.SEARCH_RADIUS;
    for (var link : PortalTopology.entrances(
        entity.level(), center.x - radius, center.z - radius, center.x + radius, center.z + radius)) {
      if (!PortalFunneling.near(link.entrance().endpoint(), center)) continue;
      double candidate = link.entrance().endpoint().center().distanceToSqr(center);
      if (candidate < distance && PortalEntityMovement.canTraverse(entity, link)) {
        distance = candidate;
        nearest = link;
      }
    }
    if (nearest != null)
      entity.setDeltaMovement(
          PortalFunneling.velocity(nearest.entrance().endpoint(), center, velocity, .05));
  }
}
