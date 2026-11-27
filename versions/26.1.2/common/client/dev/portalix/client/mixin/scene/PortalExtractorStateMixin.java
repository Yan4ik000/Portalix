package dev.portalix.client.mixin.scene;

import dev.portalix.client.compat.minecraft.SceneExtractorAccess;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LevelRenderer.class)
abstract class PortalExtractorStateMixin implements SceneExtractorAccess {
  @Unique private boolean portalix$viewInvalid;

  @Override
  public void portalix$invalidateView() {
    portalix$viewInvalid = true;
  }

  @Override
  public boolean portalix$consumeViewInvalidation() {
    boolean invalid = portalix$viewInvalid;
    portalix$viewInvalid = false;
    return invalid;
  }
}
