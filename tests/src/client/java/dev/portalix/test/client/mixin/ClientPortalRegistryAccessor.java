package dev.portalix.test.client.mixin;

import dev.portalix.client.portal.ClientPortalRegistry;
import java.util.Map;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ClientPortalRegistry.class, remap = false)
public interface ClientPortalRegistryAccessor {
  @Accessor("portals") Map<?, ?> portalix$visuals();
  @Accessor("closing") List<?> portalix$closing();
}
