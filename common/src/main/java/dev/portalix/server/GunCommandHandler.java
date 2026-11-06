package dev.portalix.server;

import dev.portalix.gun.GunFireTiming;
import dev.portalix.gun.GunPermissions;
import dev.portalix.item.PortalixIdentity;
import dev.portalix.network.gun.GunCommand;
import dev.portalix.network.gun.GunEventKind;
import dev.portalix.server.carry.CarryController;
import dev.portalix.server.portal.GunPortalRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

final class GunCommandHandler {
  private final Map<UUID, Long> lastAcceptedCommandTick = new HashMap<>();
  private final Map<UUID, Long> lastGrabTick = new HashMap<>();
  private final PortalShotManager shots;
  private final GunStateSync stateSync;

  private final CarryController carryController;
  private final GunPortalRegistry portalRegistry;
  private final GunStateBroadcaster broadcaster;

  GunCommandHandler(
      CarryController carryController,
      GunPortalRegistry portalRegistry,
      PortalShotManager shots,
      GunStateBroadcaster broadcaster,
      GunStateSync stateSync) {
    this.carryController = carryController;
    this.portalRegistry = portalRegistry;
    this.broadcaster = broadcaster;
    this.shots = shots;
    this.stateSync = stateSync;
  }

  void receiveCommand(ServerPlayer player, GunCommand command) {
    long tick = player.level().getServer().getTickCount();
    UUID owner = player.getUUID();
    if (command == GunCommand.SYNC_STATE) {
      stateSync.requestSync(player);
      return;
    }
    if (!GunPermissions.canUse(player)) return;
    UUID gun = PortalixIdentity.ensure(player.getMainHandItem());
    Map<UUID, Long> cooldown =
        command == GunCommand.TOGGLE_GRAB ? lastGrabTick : lastAcceptedCommandTick;
    boolean immediate = command == GunCommand.RELEASE || command == GunCommand.RESET;
    if (!immediate) {
      Long previous = cooldown.get(owner);
      if (previous != null && tick - previous < GunFireTiming.ACTION_COOLDOWN_TICKS) return;
      cooldown.put(owner, tick);
    }
    switch (command) {
      case TOGGLE_GRAB -> carryController.toggleGrab(player);
      case RELEASE -> carryController.requestRelease(player, false);
      case RESET -> {
        boolean changed = shots.cancel(gun);
        changed |= carryController.requestRelease(player, false);
        changed |= portalRegistry.clear(gun);
        if (changed) broadcaster.sendEvent(player, GunEventKind.RESET);
        else stateSync.requestSync(player);
      }
      case FIRE_BLUE, FIRE_ORANGE -> {
        if (carryController.isHolding(owner))
          carryController.requestRelease(player, command == GunCommand.FIRE_BLUE);
        else shots.fire(player, command == GunCommand.FIRE_ORANGE);
      }
    }
  }

  void disconnect(ServerPlayer player) {
    this.lastAcceptedCommandTick.remove(player.getUUID());
    this.lastGrabTick.remove(player.getUUID());
  }

  void clear() {
    this.lastAcceptedCommandTick.clear();
    this.lastGrabTick.clear();
  }
}
