package dev.portalix.test.client.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "dev.portalix.client.portal.scene.PortalRenderScene", remap = false)
public interface PortalRenderSceneAccessor {
  @Accessor("renderer") LevelRenderer portalix$renderer();
  @Accessor("renderedThisFrame") boolean portalix$rendered();
  @Accessor("destinationReady") boolean portalix$destinationReady();
}
