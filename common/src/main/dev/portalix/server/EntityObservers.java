package dev.portalix.server;

import dev.portalix.platform.PortalPlatform;
import java.util.Collection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class EntityObservers {
  private EntityObservers() {}

  public static Collection<ServerPlayer> tracking(Entity entity) {
    return PortalPlatform.INSTANCE.trackingPlayers(entity);
  }
}
