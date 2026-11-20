package dev.portalix.client.mixin.gun;

import dev.portalix.client.PortalixClient;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItems.class)
abstract class GunHandEquipMixin {
  @Shadow private ItemStack mainHandItem;
  @Shadow private float mainHandHeight;
  @Shadow private float oMainHandHeight;

  @Inject(method = "tick", at = @At("TAIL"))
  private void portalix$deferItemEquip(LocalPlayer player, CallbackInfo ci) {
    if (!PortalixClient.displayedGun().isEmpty()) {
      mainHandItem = player.getMainHandItem();
      mainHandHeight = 0.0F;
      oMainHandHeight = 0.0F;
    }
  }
}
