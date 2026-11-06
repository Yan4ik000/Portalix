package dev.portalix.mixin.gun;

import dev.portalix.PortalixMod;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class GunInventoryRestrictionMixin {
  @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
  private void portalix$swap(
      int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
    AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
    if (input == ContainerInput.SWAP
        && button == Inventory.SLOT_OFFHAND
        && slot >= 0
        && slot < menu.slots.size()
        && PortalixMod.isPortalix(menu.slots.get(slot).getItem())) {
      ci.cancel();
    }
  }
}
