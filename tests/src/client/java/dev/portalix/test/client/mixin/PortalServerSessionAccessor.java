package dev.portalix.test.client.mixin;

import dev.portalix.server.PortalServerSession;
import dev.portalix.server.portal.GunPortalRegistry;
import dev.portalix.server.carry.CarryController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = PortalServerSession.class, remap = false)
public interface PortalServerSessionAccessor {
  @Accessor("portalRegistry") GunPortalRegistry portalix$registry();
  @Accessor("carryController") CarryController portalix$carryController();
}
