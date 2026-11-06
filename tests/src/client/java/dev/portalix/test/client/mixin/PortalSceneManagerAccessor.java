package dev.portalix.test.client.mixin;

import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.portal.PortalLink;
import java.util.List;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = PortalSceneManager.class, remap = false)
public interface PortalSceneManagerAccessor {
  @Accessor("SCENES") static Map<List<PortalLink>, ?> portalix$scenes() { throw new AssertionError(); }
}
