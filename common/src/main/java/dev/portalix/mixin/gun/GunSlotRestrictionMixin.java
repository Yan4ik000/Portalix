package dev.portalix.mixin.gun;

import dev.portalix.PortalixMod;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class GunSlotRestrictionMixin {
  @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
  private void portalix$offhand(ItemStack stack, CallbackInfoReturnable<Boolean> ci) {
    Slot slot = (Slot) (Object) this;
    if (slot.container instanceof Inventory
        && slot.getContainerSlot() == Inventory.SLOT_OFFHAND
        && PortalixMod.isPortalix(stack)) {
      ci.setReturnValue(false);
    }
  }
}
