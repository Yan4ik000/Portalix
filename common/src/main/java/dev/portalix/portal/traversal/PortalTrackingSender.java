package dev.portalix.portal.traversal;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public final class PortalTrackingSender implements ServerEntity.Synchronizer {
  private final Entity entity;
  private final ServerEntity.Synchronizer delegate;
  private @Nullable Map<ServerPlayer, List<Packet<? super ClientGamePacketListener>>> updates;

  public PortalTrackingSender(Entity entity, ServerEntity.Synchronizer delegate) {
    this.entity = entity;
    this.delegate = delegate;
  }

  public void begin() {
    updates = PortalCrossingSync.beginTracking(entity);
  }

  public void finish() {
    var completed = updates;
    updates = null;
    if (completed != null) PortalCrossingSync.afterTracking(entity, completed);
  }

  public void clear() {
    updates = null;
  }

  @Override
  public void sendToTrackingPlayers(Packet<? super ClientGamePacketListener> packet) {
    if (updates == null) delegate.sendToTrackingPlayers(packet);
    else PortalCrossingSync.collectTracking(entity, updates, packet, false, null);
  }

  @Override
  public void sendToTrackingPlayersAndSelf(Packet<? super ClientGamePacketListener> packet) {
    if (updates == null) delegate.sendToTrackingPlayersAndSelf(packet);
    else PortalCrossingSync.collectTracking(entity, updates, packet, true, null);
  }

  @Override
  public void sendToTrackingPlayersFiltered(
      Packet<? super ClientGamePacketListener> packet, Predicate<ServerPlayer> predicate) {
    if (updates == null) delegate.sendToTrackingPlayersFiltered(packet, predicate);
    else PortalCrossingSync.collectTracking(entity, updates, packet, false, predicate);
  }
}
