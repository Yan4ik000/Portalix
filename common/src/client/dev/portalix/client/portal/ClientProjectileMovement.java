package dev.portalix.client.portal;

import dev.portalix.client.ClientNetworking;
import dev.portalix.network.portal.ProjectilePortalMovePayload;
import dev.portalix.network.portal.ProjectileStatePayload;
import dev.portalix.portal.traversal.PortalEntityMovement;
import dev.portalix.portal.traversal.ProjectileStateSync;
import net.minecraft.world.entity.projectile.Projectile;

public final class ClientProjectileMovement {
  private ClientProjectileMovement() {}

  public static void register(ClientNetworking.Registration registration) {
    ProjectileStateSync.setPredictionListener(
        entity ->
            ClientNetworking.sendToServer(
                new ProjectilePortalMovePayload(entity.getId(), entity.getUUID())));
    registration.register(
        ProjectileStatePayload.TYPE,
        (packet, context) -> {
          var level = context.level;
          var entity = level == null ? null : level.getEntity(packet.entityId());
          if (entity instanceof Projectile && entity.getUUID().equals(packet.entityUuid()))
            PortalEntityMovement.projectileSnapshot(entity, packet);
        });
  }
}
