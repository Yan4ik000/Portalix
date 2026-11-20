package dev.portalix.client.mixin.gun;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.portalix.PortalixMod;
import dev.portalix.client.PortalixClient;
import dev.portalix.client.gun.render.PortalixItemRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class GunHandRenderMixin {
  @Shadow @Final private Minecraft minecraft;
  @Shadow private ItemStack mainHandItem;
  @Shadow private float mainHandHeight;
  @Shadow private float oMainHandHeight;

  @Inject(method = "tick", at = @At("TAIL"))
  private void portalix$deferItemEquip(CallbackInfo ci) {
    if (!PortalixClient.displayedGun().isEmpty()) {
      mainHandItem = minecraft.player.getMainHandItem();
      mainHandHeight = 0.0F;
      oMainHandHeight = 0.0F;
    }
  }

  @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
  private void portalix$hand(
      AbstractClientPlayer player,
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
            pose, collector, light, partial, player.getMainArm() == HumanoidArm.LEFT, displayed);
        ci.cancel();
      } else if (PortalixMod.isPortalix(stack)) {
        ci.cancel();
      }
    } else if (PortalixMod.isHoldingPortalix(player) || PortalixMod.isPortalix(stack)) {
      ci.cancel();
    }
  }
}
