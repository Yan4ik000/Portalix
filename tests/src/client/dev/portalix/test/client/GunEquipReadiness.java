package dev.portalix.test.client;

import dev.portalix.client.PortalixClient;
import dev.portalix.item.PortalixIdentity;
import dev.portalix.item.PortalixItem;
import dev.portalix.item.PortalixVariant;
import java.util.UUID;
import net.minecraft.client.Minecraft;

final class GunEquipReadiness {
  private GunEquipReadiness() {}

  static boolean ready(Minecraft client, UUID identity, PortalixVariant expected) {
    var held = client.player.getMainHandItem();
    var displayed = PortalixClient.displayedGun();
    return !held.isEmpty() && !displayed.isEmpty()
        && held.getItem() instanceof PortalixItem gun && gun.variant() == expected
        && displayed.getItem() == held.getItem()
        && identity.equals(PortalixIdentity.get(held)) && identity.equals(PortalixIdentity.get(displayed))
        && !PortalixClient.localAnimation().isDrawing() && !PortalixClient.localAnimation().isHolstering();
  }
}
