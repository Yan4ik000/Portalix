package dev.portalix.gun;

import dev.portalix.PortalixMod;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

public final class GunPermissions {
  private GunPermissions() {}

  public static boolean canUse(Player player) {
    return player != null
        && player.isAlive()
        && player.gameMode() != null
        && !player.isSpectator()
        && PortalixMod.isHoldingPortalix(player);
  }

  public static boolean canCarry(Player player) {
    return canUse(player) && player.gameMode() != GameType.ADVENTURE && player.mayBuild();
  }
}
