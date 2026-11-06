package dev.portalix.client.mixin.entity;

import dev.portalix.client.portal.entity.PortalEntityScenes;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class PortalEntityExtractionMixin {
  @Inject(method = "extractEntity", at = @At("RETURN"))
  private void portalix$remember(
      Entity entity, float partial, CallbackInfoReturnable<EntityRenderState> cir) {
    PortalEntityScenes.onEntityExtracted(entity, cir.getReturnValue(), partial);
  }
}
