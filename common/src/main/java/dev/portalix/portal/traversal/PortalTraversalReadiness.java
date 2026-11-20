package dev.portalix.portal.traversal;

import dev.portalix.carry.CarryBindings;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalViewLimits;
import dev.portalix.portal.query.PlayerPortalVisibility;
import java.util.function.BiPredicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

public final class PortalTraversalReadiness {
  private static BiPredicate<Entity, PortalLink> client = (entity, link) -> false;

  private PortalTraversalReadiness() {}

  public static void setClientReadiness(BiPredicate<Entity, PortalLink> predicate) {
    client = predicate;
  }

  public static boolean ready(Entity entity, PortalLink link) {
    if (entity.level().isClientSide()) return client.test(entity, link);
    return playerReady(entity, link) && exitLoaded(entity, link);
  }

  static boolean convoyReady(Entity root, PortalLink link) {
    boolean clientSide = root.level().isClientSide();
    return membersReady(root, link, clientSide) && (clientSide || exitLoaded(root, link));
  }

  private static boolean membersReady(Entity entity, PortalLink link, boolean clientSide) {
    if (!(clientSide ? client.test(entity, link) : playerReady(entity, link))) return false;
    for (var passenger : entity.getPassengers())
      if (!membersReady(passenger, link, clientSide)) return false;
    return true;
  }

  private static boolean playerReady(Entity entity, PortalLink link) {
    if (entity instanceof ServerPlayer player) return PlayerPortalVisibility.of(player).isReady(link);
    var holder = entity.level().getEntity(CarryBindings.holderId(entity));
    return !(holder instanceof ServerPlayer player) || PlayerPortalVisibility.of(player).isReady(link);
  }

  private static boolean exitLoaded(Entity entity, PortalLink link) {
    var chunks = entity.level().getChunkSource();
    return PortalViewLimits.hasExitCore(link.exit().endpoint(), key ->
        chunks.getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key)) != null);
  }
}
