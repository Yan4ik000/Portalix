package dev.portalix.server;

import dev.portalix.PortalixMod;
import dev.portalix.item.PortalixIdentity;
import dev.portalix.server.carry.CarryController;
import dev.portalix.server.portal.GunPortalRegistry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

final class GunStateSync {
  private final Map<UUID, Set<UUID>> trackedOwners = new HashMap<>();
  private final StateSyncScheduler stateSync = new StateSyncScheduler(20);
  private final Map<UUID, UUID> equippedGuns = new HashMap<>();
  private final CarryController carryController;
  private final GunPortalRegistry portalRegistry;
  private final PortalShotManager shots;
  private final GunStateBroadcaster broadcaster;

  GunStateSync(
      CarryController carryController,
      GunPortalRegistry portalRegistry,
      PortalShotManager shots,
      GunStateBroadcaster broadcaster) {
    this.carryController = carryController;
    this.portalRegistry = portalRegistry;
    this.shots = shots;
    this.broadcaster = broadcaster;
  }

  void requestSync(ServerPlayer player) {
    if (stateSync.requestSync(player.getUUID(), player.level().getServer().getTickCount())) {
      sendFullState(player);
    }
  }

  private void sendFullState(ServerPlayer player) {
    broadcaster.sendSnapshot(player, player);
    portalRegistry.synchronize(player);
    carryController.synchronize(player);
    shots.synchronize(player, true);

    for (UUID ownerId : this.trackedOwners.getOrDefault(player.getUUID(), Set.of())) {
      ServerPlayer other = player.level().getServer().getPlayerList().getPlayer(ownerId);
      if (other != null && other != player && other.level() == player.level())
        broadcaster.sendSnapshot(other, player);
    }
  }

  void onTrackingStarted(ServerPlayer owner, ServerPlayer observer) {
    this.trackedOwners
        .computeIfAbsent(observer.getUUID(), key -> new HashSet<>())
        .add(owner.getUUID());
    broadcaster.sendSnapshot(owner, observer);
    shots.synchronize(observer, false);
  }

  void onTrackingStopped(ServerPlayer owner, ServerPlayer observer) {
    Set<UUID> owners = this.trackedOwners.get(observer.getUUID());
    if (owners != null) {
      owners.remove(owner.getUUID());
      if (owners.isEmpty()) this.trackedOwners.remove(observer.getUUID());
    }
  }

  void flushPending(MinecraftServer server) {
    this.stateSync.flushPending(
        server.getTickCount(),
        owner -> {
          ServerPlayer player = server.getPlayerList().getPlayer(owner);
          if (player != null) {
            sendFullState(player);
          }
        });
  }

  void synchronizeEquipment(MinecraftServer server, Set<UUID> changedPortals) {
    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
      UUID gun =
          PortalixMod.isHoldingPortalix(player)
              ? PortalixIdentity.ensure(player.getMainHandItem())
              : null;
      UUID previous =
          gun == null
              ? equippedGuns.remove(player.getUUID())
              : equippedGuns.put(player.getUUID(), gun);
      if (!Objects.equals(previous, gun) || gun != null && changedPortals.contains(gun)) {
        broadcaster.sendState(player);
        portalRegistry.synchronize(player);
      }
    }
  }

  void disconnect(ServerPlayer player) {
    UUID owner = player.getUUID();
    trackedOwners.remove(owner);
    trackedOwners.values().removeIf(
        owners -> {
          owners.remove(owner);
          return owners.isEmpty();
        });
    stateSync.forget(owner);
    equippedGuns.remove(owner);
  }

  void clear() {
    trackedOwners.clear();
    stateSync.clear();
    equippedGuns.clear();
  }
}
