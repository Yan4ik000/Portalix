package dev.portalix.portal.traversal;

import dev.portalix.network.ModNetworking;
import dev.portalix.network.portal.ProjectilePortalMovePayload;
import dev.portalix.network.portal.ProjectileStatePayload;
import dev.portalix.server.EntityObservers;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

public final class ProjectileStateSync {
  private static Consumer<Entity> clientPrediction = entity -> {};

  private ProjectileStateSync() {}

  public static void requestSnapshot(ServerPlayer player, ProjectilePortalMovePayload request) {
    var entity = player.level().getEntity(request.entityId());
    if (!(entity instanceof Projectile)
        || !entity.getUUID().equals(request.entityUuid())
        || !EntityObservers.tracking(entity).contains(player)) return;
    var state = EntityPortalState.of(entity);
    if (state.snapshotObservers == null) state.snapshotObservers = new ReferenceOpenHashSet<>(2);
    state.snapshotObservers.add(player);
  }

  public static void entityRemoved(Entity entity) {
    EntityPortalState.of(entity).snapshotObservers = null;
  }

  public static void trackingStopped(Entity entity, ServerPlayer observer) {
    var state = EntityPortalState.of(entity);
    var requests = state.snapshotObservers;
    if (requests != null && requests.remove(observer) && requests.isEmpty())
      state.snapshotObservers = null;
  }

  public static void setPredictionListener(Consumer<Entity> prediction) {
    clientPrediction = prediction;
  }

  static void predicted(Entity entity) {
    clientPrediction.accept(entity);
  }

  public static void afterTracking(Entity entity) {
    if (!(entity instanceof Projectile)) return;
    var state = EntityPortalState.of(entity);
    var requests = state.snapshotObservers;
    if (requests == null) return;
    state.snapshotObservers = null;
    var packet =
        new ProjectileStatePayload(
            entity.getId(),
            entity.getUUID(),
            entity.level().getGameTime(),
            state.sequence,
            entity.position(),
            entity.getDeltaMovement(),
            entity.getYRot(),
            entity.getXRot());
    for (var observer : EntityObservers.tracking(entity))
      if (requests.contains(observer))
        ModNetworking.sendToPlayer(observer, packet);
  }
}
