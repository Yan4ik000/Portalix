package dev.portalix.test.client.mixin;

import dev.portalix.client.portal.render.PortalOpeningAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "dev.portalix.client.portal.PortalVisual", remap = false)
public interface PortalVisualAccessor {
  @Accessor("identity") long portalix$identity();
  @Accessor("animation") PortalOpeningAnimation portalix$animation();
}
