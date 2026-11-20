package dev.portalix.server;

import dev.portalix.api.PortalColor;
import dev.portalix.item.PortalixIdentity;
import dev.portalix.network.gun.GunCommand;
import dev.portalix.network.gun.GunStatePayload;
import dev.portalix.network.portal.PortalCameraViewDemandPayload;
import dev.portalix.network.portal.PortalViewDemandPayload;
import dev.portalix.network.portal.PortalViewReadyPayload;
import dev.portalix.portal.PortalLink;
import dev.portalix.server.carry.CarryController;
import dev.portalix.server.portal.GunPortalRegistry;
import dev.portalix.server.portal.PortalChunkTracker;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class PortalServerSession {
  private final CarryController carryController;
  private final GunPortalRegistry portalRegistry;
  private final PortalShotManager shots;
  private final GunStateSync stateSync;
  private final GunCommandHandler gunCommands;
  private final PortalChunkTracker chunkTracker;

  public PortalServerSession(MinecraftServer server) {
    portalRegistry = new GunPortalRegistry(server);
    chunkTracker = new PortalChunkTracker(portalRegistry);
    var broadcaster = new GunStateBroadcaster(this::snapshot);
    carryController = new CarryController(broadcaster);
    shots = new PortalShotManager(portalRegistry, broadcaster);
    stateSync = new GunStateSync(carryController, portalRegistry, shots, broadcaster);
    gunCommands = new GunCommandHandler(carryController, portalRegistry, shots, broadcaster, stateSync);
  }

  public void tick(MinecraftServer server) {
    shots.tick(server);
    stateSync.flushPending(server);
    stateSync.synchronizeEquipment(server, portalRegistry.tick());
    chunkTracker.tick(server);
    carryController.tick(server);
  }

  public void portalsClosing(ServerLevel level, List<PortalLink> links) {
    carryController.portalsClosing(level, links);
  }

  public void observerMoved(ServerPlayer player) {
    chunkTracker.moveStarted(player);
  }

  public void observerMoved(ServerPlayer player, int radius) {
    chunkTracker.moveStarted(player, radius);
  }

  public void observerMoveFinished(ServerPlayer player) {
    if (chunkTracker.moveFinished(player)) portalRegistry.observerMoved(player);
  }

  public void chunkRefreshConfirmed(ServerPlayer player) {
    chunkTracker.chunkRefreshConfirmed(player);
  }

  public void chunkDeliveryChanged(ServerPlayer player, long chunk) {
    portalRegistry.chunkDeliveryChanged(player, chunk);
  }

  public boolean retainsTerrain(ServerPlayer player, long chunk) {
    return chunkTracker.retainsTerrain(player, chunk);
  }

  public boolean retainedDuringMove(ServerPlayer player, long chunk) {
    return chunkTracker.retainedDuringMove(player, chunk);
  }

  public void acknowledgeView(ServerPlayer player, PortalViewReadyPayload payload) {
    chunkTracker.acknowledgeView(player, payload);
  }

  public void requestViews(ServerPlayer player, PortalViewDemandPayload payload) {
    chunkTracker.requestViews(player, payload);
  }

  public void requestCameraViews(ServerPlayer player, PortalCameraViewDemandPayload payload) {
    chunkTracker.requestCameraViews(player, payload);
  }

  public void clientTickEnded(ServerPlayer player) {
    chunkTracker.clientTickEnded(player);
  }

  public void cameraCrossed(ServerPlayer player, PortalLink link) {
    chunkTracker.cameraCrossed(player, link);
  }

  public void resetCamera(ServerPlayer player) {
    chunkTracker.resetCamera(player);
  }

  public void handle(ServerPlayer player, GunCommand command) {
    gunCommands.receiveCommand(player, command);
  }

  public void trackingStarted(Entity entity, ServerPlayer observer) {
    carryController.trackingStarted(entity, observer);
    if (entity instanceof ServerPlayer owner) {
      portalRegistry.observerMoved(observer);
      stateSync.onTrackingStarted(owner, observer);
    }
  }

  public void observePortal(ServerPlayer observer, UUID gun) {
    portalRegistry.observePortal(observer, gun);
  }

  public void trackingStopped(ServerPlayer owner, ServerPlayer observer) {
    stateSync.onTrackingStopped(owner, observer);
  }

  public void disconnect(ServerPlayer player) {
    chunkTracker.disconnect(player);
    carryController.drop(player.getUUID(), player);
    gunCommands.disconnect(player);
    stateSync.disconnect(player);
    shots.disconnect(player.getUUID());
    portalRegistry.forget(player.getUUID());
  }

  public void clear() {
    chunkTracker.close();
    try {
      carryController.clear();
    } finally {
      gunCommands.clear();
      stateSync.clear();
      shots.clear();
      portalRegistry.clearAll();
    }
  }

  private GunStatePayload snapshot(ServerPlayer owner) {
    UUID ownerId = owner.getUUID();
    UUID gun = PortalixIdentity.get(owner.getMainHandItem());
    return new GunStatePayload(
        owner.getId(),
        ownerId,
        carryController.targetId(ownerId),
        carryController.targetUuid(ownerId),
        carryController.holdTicks(ownerId),
        portalRegistry.hudMask(gun),
        portalRegistry.latestColor(gun) == PortalColor.ORANGE);
  }
}
