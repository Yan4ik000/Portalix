package dev.portalix.client.mixin.gun;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.portalix.PortalixMod;
import dev.portalix.client.PortalixClient;
import dev.portalix.client.gun.render.PortalixItemRenderer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class GunHandRenderMixin {
  @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
  private void portalix$hand(
      PlayerRenderState player,
      FirstPersonHandsAndItemsRenderState state,
      float partial,
      float pitch,
      InteractionHand hand,
      float attack,
      ItemStack stack,
      float equip,
      PoseStack pose,
      SubmitNodeCollector collector,
      int light,
      CallbackInfo ci) {
    if (hand == InteractionHand.MAIN_HAND) {
      ItemStack displayed = PortalixClient.displayedGun();
      if (!displayed.isEmpty()) {
        PortalixItemRenderer.hand(
            pose, collector, light, partial, player.avatarRenderState.mainArm == HumanoidArm.LEFT,
            displayed);
        ci.cancel();
      } else if (PortalixMod.isPortalix(stack)) {
        ci.cancel();
      }
    } else if (PortalixMod.isPortalix(state.mainHandItem) || PortalixMod.isPortalix(stack)) {
      ci.cancel();
    }
  }
}
