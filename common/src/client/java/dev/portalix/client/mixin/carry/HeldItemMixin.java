package dev.portalix.client.mixin.carry;

import dev.portalix.client.PortalixClient;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class HeldItemMixin {
  @Inject(
      method =
          "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
      at = @At("TAIL"))
  private void portalix$steady(
      ItemEntity entity, ItemEntityRenderState state, float partial, CallbackInfo ci) {
    if (PortalixClient.carryController().owner(entity) != null) {
      state.ageInTicks = 0.0F;
      state.bobOffset = (float) (-Math.PI / 2);
    }
  }
}
