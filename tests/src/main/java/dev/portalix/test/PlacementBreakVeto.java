package dev.portalix.test;

import static dev.portalix.test.ServerRegressionSuite.check;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.placement.BlockPortalPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class PlacementBreakVeto {
  private static final ScopedValue<Veto> ACTIVE = ScopedValue.newInstance();

  private PlacementBreakVeto() {}

  public static boolean allow(Player player, BlockPos position) {
    if (!ACTIVE.isBound()) return true;
    var veto = ACTIVE.get();
    if (player != veto.player || !position.equals(veto.position)) return true;
    veto.called = true;
    return false;
  }

  static void reject(ServerPlayer player, PortalEndpoint endpoint, BlockPos position) {
    var veto = new Veto(player, position);
    ScopedValue.where(ACTIVE, veto).run(() ->
        check(!BlockPortalPlacement.prepare(player, endpoint), "Placement ignored the loader's block-break veto"));
    check(veto.called, "Placement did not invoke the loader's before-break event");
  }

  private static final class Veto {
    private final ServerPlayer player;
    private final BlockPos position;
    private boolean called;

    private Veto(ServerPlayer player, BlockPos position) {
      this.player = player;
      this.position = position;
    }
  }
}
