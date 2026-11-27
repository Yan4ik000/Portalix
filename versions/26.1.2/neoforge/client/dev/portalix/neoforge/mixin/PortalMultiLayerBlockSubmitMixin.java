package dev.portalix.neoforge.mixin;

import dev.portalix.client.portal.entity.PortalEntityClipMask;
import dev.portalix.client.portal.entity.PortalEntityClipping;
import dev.portalix.client.portal.entity.PortalEntitySubmission;
import net.neoforged.neoforge.client.extensions.SubmitNodeStorageExtension.MultiLayerBlockModelSubmit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MultiLayerBlockModelSubmit.class)
abstract class PortalMultiLayerBlockSubmitMixin implements PortalEntitySubmission {
  @Unique private final PortalEntityClipMask portalix$mask = PortalEntityClipping.submitting();

  @Override
  public PortalEntityClipMask portalix$clipMask() {
    return portalix$mask;
  }
}
