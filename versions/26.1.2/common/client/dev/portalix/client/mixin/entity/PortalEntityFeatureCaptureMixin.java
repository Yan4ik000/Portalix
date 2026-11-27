package dev.portalix.client.mixin.entity;

import dev.portalix.client.portal.entity.PortalEntityClipMask;
import dev.portalix.client.portal.entity.PortalEntityClipping;
import dev.portalix.client.portal.entity.PortalEntitySubmission;
import net.minecraft.client.renderer.SubmitNodeStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({SubmitNodeStorage.ModelSubmit.class, SubmitNodeStorage.TranslucentModelSubmit.class,
    SubmitNodeStorage.ModelPartSubmit.class, SubmitNodeStorage.ItemSubmit.class,
    SubmitNodeStorage.ShadowSubmit.class, SubmitNodeStorage.FlameSubmit.class,
    SubmitNodeStorage.LeashSubmit.class, SubmitNodeStorage.NameTagSubmit.class,
    SubmitNodeStorage.TextSubmit.class, SubmitNodeStorage.MovingBlockSubmit.class,
    SubmitNodeStorage.BlockModelSubmit.class,
    SubmitNodeStorage.BreakingBlockModelSubmit.class, SubmitNodeStorage.CustomGeometrySubmit.class})
abstract class PortalEntityFeatureCaptureMixin implements PortalEntitySubmission {
  @Unique private final PortalEntityClipMask portalix$mask = PortalEntityClipping.submitting();

  @Override
  public PortalEntityClipMask portalix$clipMask() {
    return portalix$mask;
  }
}
