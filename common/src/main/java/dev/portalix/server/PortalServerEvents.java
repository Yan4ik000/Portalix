package dev.portalix.server;

import dev.portalix.carry.CarryBindings;
import dev.portalix.network.carry.CarryInputPayload;
import dev.portalix.network.gun.GunCommand;
import dev.portalix.network.portal.PortalViewReadyPayload;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalLinkId;
import dev.portalix.portal.traversal.PortalCrossingSync;
import dev.portalix.portal.traversal.ProjectileStateSync;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public final class PortalServerEvents {
  private static PortalServerSession session;

  private PortalServerEvents() {}

  public static void receiveCarryInput(ServerPlayer player, CarryInputPayload input) {
    if (session != null) session.carryInput(player, input);
  }

  public static void receiveGunCommand(ServerPlayer player, GunCommand command) {
    if (session != null) session.handle(player, command);
  }

  public static void started(MinecraftServer server) {
    session = new PortalServerSession(server);
  }

  public static void stopping(MinecraftServer server) {
    PortalServerSession previous = session;
    session = null;
    if (previous != null) previous.clear();
  }

  public static void stopped(MinecraftServer server) {
    PortalCrossingSync.clear();
  }

  public static void tick(MinecraftServer server) {
    PortalCrossingSync.flushPassengerUpdates();
    if (session != null) session.tick(server);
  }

  public static void disconnected(ServerPlayer player) {
    ProjectileStateSync.disconnected(player);
    if (session != null) session.disconnect(player);
  }

  public static void entityRemoved(Entity entity, Level level) {
    if (level instanceof ServerLevel serverLevel) {
      ProjectileStateSync.entityRemoved(entity);
      CarryBindings.unbindEntity(serverLevel, entity);
    }
  }

  public static void trackingStarted(Entity entity, ServerPlayer observer) {
    if (session != null) session.trackingStarted(entity, observer);
  }

  public static void trackingStopped(Entity entity, ServerPlayer observer) {
    PortalCrossingSync.trackingStopped(entity, observer);
    if (session != null && entity instanceof ServerPlayer owner)
      session.trackingStopped(owner, observer);
  }

  public static void acknowledgeView(ServerPlayer player, PortalViewReadyPayload payload) {
    if (session != null) session.acknowledgeView(player, payload);
  }

  public static void observerMoved(ServerPlayer player) {
    if (session != null) session.observerMoved(player);
  }

  public static void observerMoved(ServerPlayer player, int radius) {
    if (session != null) session.observerMoved(player, radius);
  }

  public static void observePortal(ServerPlayer observer, UUID gun) {
    if (session != null) session.observePortal(observer, gun);
  }

  public static void observePortals(ServerPlayer observer, List<PortalLinkId> route) {
    route.stream().map(PortalLinkId::gun).distinct().forEach(gun -> observePortal(observer, gun));
  }

  public static void observerMoveFinished(ServerPlayer player) {
    if (session != null) session.observerMoveFinished(player);
  }

  public static boolean retainedDuringMove(ServerPlayer player, long chunk) {
    return session != null && session.retainedDuringMove(player, chunk);
  }

  public static void portalsClosing(ServerLevel level, List<PortalLink> links) {
    if (session != null) session.portalsClosing(level, links);
  }
}
