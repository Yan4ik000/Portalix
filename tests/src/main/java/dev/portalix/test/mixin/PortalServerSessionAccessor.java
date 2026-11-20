package dev.portalix.test.mixin;

import dev.portalix.server.PortalServerSession;
import dev.portalix.server.portal.GunPortalRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = PortalServerSession.class, remap = false)
public interface PortalServerSessionAccessor {
  @Accessor("portalRegistry") GunPortalRegistry portalix$registry();
}
