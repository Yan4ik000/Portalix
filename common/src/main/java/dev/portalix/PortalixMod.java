package dev.portalix;

import dev.portalix.item.ModItems;
import dev.portalix.item.PortalixIdentity;
import dev.portalix.item.PortalixItem;
import dev.portalix.server.portal.PortalChunkTracker;
import dev.portalix.sound.GunSounds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class PortalixMod {
  public static final String MOD_ID = "portalix";

  public static boolean isPortalix(ItemStack stack) {
    return stack.getItem() instanceof PortalixItem;
  }

  public static Identifier id(String name) {
    return Identifier.fromNamespaceAndPath(MOD_ID, name);
  }

  public static boolean isHoldingPortalix(Player player) {
    return isPortalix(player.getMainHandItem());
  }

  public static void initialize() {
    PortalixIdentity.initialize();
    ModItems.initialize();
    GunSounds.register();
    PortalChunkTracker.initialize();
  }
}
