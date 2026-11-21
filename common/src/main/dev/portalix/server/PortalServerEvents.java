package dev.portalix.server;

import com.mojang.brigadier.CommandDispatcher;
import dev.portalix.carry.CarryBindings;
import dev.portalix.network.gun.GunCommand;
import dev.portalix.network.portal.PortalViewReadyPayload;
import dev.portalix.network.portal.PortalCameraViewDemandPayload;
import dev.portalix.network.portal.PortalViewDemandPayload;
import dev.portalix.network.portal.PortalViewSettingsPayload;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalLinkId;
import dev.portalix.portal.traversal.PortalCrossingSync;
import dev.portalix.portal.traversal.ProjectileStateSync;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public final class PortalServerEvents {
  private static PortalServerSession session;

  private PortalServerEvents() {}

  public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
    GunAccessCommands.register(dispatcher);
  }

  static GunAccess gunAccess(MinecraftServer server) {
    return session == null ? null : session.gunAccess(server);
  }

  static void gunAccessChanged(MinecraftServer server) {
    if (session != null) session.gunAccessChanged(server);
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
    ProjectileStateSync.trackingStopped(entity, observer);
    if (session != null && entity instanceof ServerPlayer owner)
      session.trackingStopped(owner, observer);
  }

  public static void acknowledgeView(ServerPlayer player, PortalViewReadyPayload payload) {
    if (session != null) session.acknowledgeView(player, payload);
  }

  public static void requestViews(ServerPlayer player, PortalViewDemandPayload payload) {
    if (session != null) session.requestViews(player, payload);
  }

  public static void requestViewSettings(ServerPlayer player, PortalViewSettingsPayload payload) {
    if (session != null) session.requestViewSettings(player, payload);
  }

  public static void requestCameraViews(ServerPlayer player, PortalCameraViewDemandPayload payload) {
    if (session != null) session.requestCameraViews(player, payload);
  }

  public static void clientTickEnded(ServerPlayer player) {
    if (session != null) session.clientTickEnded(player);
  }

  public static void cameraCrossed(ServerPlayer player, PortalLink link) {
    if (session != null) session.cameraCrossed(player, link);
  }

  public static void resetCamera(ServerPlayer player) {
    if (session != null) session.resetCamera(player);
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

  public static void chunkRefreshConfirmed(ServerPlayer player) {
    if (session != null) session.chunkRefreshConfirmed(player);
  }

  public static void chunkDeliveryChanged(ServerPlayer player, long chunk) {
    if (session != null) session.chunkDeliveryChanged(player, chunk);
  }

  public static boolean retainsTerrain(ServerPlayer player, long chunk) {
    return session != null && session.retainsTerrain(player, chunk);
  }

  public static boolean retainedDuringMove(ServerPlayer player, long chunk) {
    return session != null && session.retainedDuringMove(player, chunk);
  }

  public static void portalsClosing(ServerLevel level, List<PortalLink> links) {
    if (session != null) session.portalsClosing(level, links);
  }
}
