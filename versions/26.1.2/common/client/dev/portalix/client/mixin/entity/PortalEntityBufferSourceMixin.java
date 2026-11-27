package dev.portalix.client.mixin.entity;

import dev.portalix.client.portal.entity.PortalEntityClipping;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MultiBufferSource.BufferSource.class)
abstract class PortalEntityBufferSourceMixin {
  @ModifyVariable(method = "getBuffer", at = @At("HEAD"), argsOnly = true)
  private RenderType portalix$clipLayer(RenderType type) {
    return PortalEntityClipping.renderType(type);
  }
}
