package dev.portalix.server.portal;

import dev.portalix.portal.PortalLink;
import dev.portalix.portal.traversal.PortalCrossingSync;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerPortalMoveState {
  long sequence;
  long completedSequence;
  long ownerEvent;
  boolean receiving;
  int tick = Integer.MIN_VALUE;
  double travelled;
  double impulseSquared;
  ServerLevel level;

  public long completedSequence() {
    return completedSequence;
  }

  public List<PortalLink> activeCrossings(ServerPlayer player) {
    return receiving ? PortalCrossingSync.crossingsSince(player, ownerEvent) : List.of();
  }

  public void resetForWorld() {
    sequence = 0;
    completedSequence = 0;
    ownerEvent = 0;
    receiving = false;
    tick = Integer.MIN_VALUE;
    travelled = 0;
    impulseSquared = 0;
    level = null;
  }

  public interface Access {
    PlayerPortalMoveState portalix$transit();
  }
}
