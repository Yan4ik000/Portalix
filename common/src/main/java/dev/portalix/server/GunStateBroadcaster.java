package dev.portalix.server;

import dev.portalix.network.ModNetworking;
import dev.portalix.network.carry.CarryGrabPayload;
import dev.portalix.network.carry.CarryReleasePayload;
import dev.portalix.network.carry.CarryStatePayload;
import dev.portalix.network.gun.GunEventKind;
import dev.portalix.network.gun.GunEventPayload;
import dev.portalix.network.gun.GunStatePayload;
import dev.portalix.portal.traversal.PortalCrossingSync;
import dev.portalix.server.EntityObservers;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class GunStateBroadcaster {
  private final Function<ServerPlayer, GunStatePayload> snapshot;

  public GunStateBroadcaster(Function<ServerPlayer, GunStatePayload> snapshot) {
    this.snapshot = snapshot;
  }

  public void sendSnapshot(ServerPlayer owner, ServerPlayer observer) {
    if (ModNetworking.canPlayerReceive(observer, GunStatePayload.TYPE)) {
      ModNetworking.sendToPlayer(observer, snapshot.apply(owner));
    }
  }

  public void sendState(ServerPlayer owner) {
    Set<ServerPlayer> recipients = new HashSet<>(EntityObservers.tracking(owner));
    recipients.add(owner);

    for (ServerPlayer observer : recipients) {
      sendSnapshot(owner, observer);
    }
  }

  public void sendEvent(ServerPlayer player, GunEventKind kind) {
    Set<ServerPlayer> recipients = new HashSet<>(EntityObservers.tracking(player));
    recipients.add(player);

    for (ServerPlayer observer : recipients) {
      sendSnapshot(player, observer);
      if (ModNetworking.canPlayerReceive(observer, GunEventPayload.TYPE)) {
        ModNetworking.sendToPlayer(observer, new GunEventPayload(player.getId(), kind));
      }
    }
  }

  public void sendCarryState(Entity entity, ServerPlayer owner, CarryStatePayload update) {
    Set<ServerPlayer> recipients = new HashSet<>(EntityObservers.tracking(entity));
    if (owner != null && owner.level() == entity.level()) recipients.add(owner);
    for (ServerPlayer observer : recipients) {
      if (ModNetworking.canPlayerReceive(observer, CarryStatePayload.TYPE))
        PortalCrossingSync.sendAfterCrossing(entity, observer, update);
    }
  }

  public void sendGrab(Entity entity, ServerPlayer owner, CarryGrabPayload grab) {
    Set<ServerPlayer> recipients = new HashSet<>(EntityObservers.tracking(entity));
    recipients.add(owner);
    for (ServerPlayer observer : recipients) {
      if (ModNetworking.canPlayerReceive(observer, CarryGrabPayload.TYPE)) {
        PortalServerEvents.observePortals(observer, grab.route());
        PortalCrossingSync.sendAfterCrossing(entity, observer, grab);
      }
    }
  }

  public void sendRelease(
      Entity entity,
      ServerPlayer owner,
      boolean thrown,
      long generation,
      long sequence,
      long tick) {
    Set<ServerPlayer> recipients = new HashSet<>(EntityObservers.tracking(entity));
    if (owner != null) recipients.add(owner);
    CarryReleasePayload release =
        new CarryReleasePayload(
            entity.getId(),
            entity.getUUID(),
            generation,
            entity.position(),
            entity.getDeltaMovement(),
            thrown,
            sequence,
            tick);
    for (ServerPlayer observer : recipients) {
      if (ModNetworking.canPlayerReceive(observer, CarryReleasePayload.TYPE))
        PortalCrossingSync.sendAfterCrossing(entity, observer, release);
    }
  }
}
