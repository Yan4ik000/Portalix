package dev.portalix.mixin.gun;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.item.PortalixIdentity;
import java.util.UUID;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class GunCreativeIdentityMixin {
  @Shadow public ServerPlayer player;

  @WrapOperation(
      method = "handleSetCreativeModeSlot",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/protocol/game/ServerboundSetCreativeModeSlotPacket;itemStack()Lnet/minecraft/world/item/ItemStack;"),
      allow = 1)
  private ItemStack portalix$creativeIdentity(
      ServerboundSetCreativeModeSlotPacket packet, Operation<ItemStack> original) {
    ItemStack stack = original.call(packet);
    UUID id = PortalixIdentity.get(stack);
    if (id == null) return stack;
    for (int slot = 0; slot < player.inventoryMenu.slots.size(); slot++) {
      if (slot != packet.slotNum()
          && id.equals(PortalixIdentity.get(player.inventoryMenu.getSlot(slot).getItem()))) {
        stack = stack.copy();
        stack.remove(PortalixIdentity.ID.get());
        PortalixIdentity.ensure(stack);
        break;
      }
    }
    return stack;
  }
}
