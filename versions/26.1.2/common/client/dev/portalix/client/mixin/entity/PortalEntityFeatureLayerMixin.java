package dev.portalix.client.mixin.entity;

import dev.portalix.client.portal.entity.PortalEntityClipping;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SubmitNodeCollection.class)
abstract class PortalEntityFeatureLayerMixin {
  @ModifyVariable(method = {"submitModel", "submitModelPart", "submitCustomGeometry"},
      at = @At("HEAD"), argsOnly = true, require = 3, allow = 3)
  private RenderType portalix$clipLayer(RenderType type) {
    return PortalEntityClipping.submissionType(type);
  }
}
