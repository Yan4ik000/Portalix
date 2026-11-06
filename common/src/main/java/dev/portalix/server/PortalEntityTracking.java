package dev.portalix.server;

import dev.portalix.mixin.world.tracking.ChunkMapAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;

public final class PortalEntityTracking {
  private PortalEntityTracking() {}

  public static boolean ownerAvailable(Entity entity, ServerPlayer observer) {
    if (!(entity instanceof FishingHook hook)) return true;
    var owner = hook.getPlayerOwner();
    return owner != null && owner.isAlive() && owner.fishing == hook && owner.level() == hook.level()
        && owner.level() == observer.level()
        && (owner == observer || owner.broadcastToPlayer(observer) && tracker(owner) != null);
  }

  public static boolean requiredOwner(Entity entity, ServerPlayer observer) {
    if (!(entity instanceof ServerPlayer owner)) return false;
    var hook = owner.fishing;
    if (hook == null || !hook.isAlive() || hook.level() != owner.level()
        || hook.level() != observer.level() || hook.getPlayerOwner() != owner) return false;
    var tracker = tracker(hook);
    return tracker != null && tracker.portalix$hasObserver(observer);
  }

  public static void updateOwner(Entity entity, ServerPlayer observer) {
    if (!(entity instanceof FishingHook hook)) return;
    var owner = hook.getPlayerOwner();
    if (owner == null || owner == observer || owner.level() != hook.level()
        || owner.level() != observer.level()) return;
    var tracker = tracker(owner);
    if (tracker != null) tracker.updatePlayer(observer);
  }

  private static Tracker tracker(Entity entity) {
    var level = (ServerLevel) entity.level();
    return (Tracker) ((ChunkMapAccessor) level.getChunkSource().chunkMap)
        .portalix$entityTrackers().get(entity.getId());
  }

  public interface Tracker {
    boolean portalix$hasObserver(ServerPlayer observer);

    void updatePlayer(ServerPlayer observer);
  }
}
