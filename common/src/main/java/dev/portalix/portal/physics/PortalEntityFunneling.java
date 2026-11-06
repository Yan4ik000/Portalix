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
    var center = entity.getBoundingBox().getCenter();
    var velocity = entity.getDeltaMovement();
    PortalLink nearest = null;
    double distance = Double.POSITIVE_INFINITY;
    for (var link : PortalTopology.links(entity.level())) {
      if (!PortalEntityMovement.canTraverse(entity, link)
          || !PortalFunneling.eligible(link.entrance().endpoint(), center, velocity)) continue;
      double candidate = link.entrance().endpoint().center().distanceToSqr(center);
      if (candidate < distance) {
        distance = candidate;
        nearest = link;
      }
    }
    if (nearest != null)
      entity.setDeltaMovement(
          PortalFunneling.velocity(nearest.entrance().endpoint(), center, velocity, .05));
  }
}
