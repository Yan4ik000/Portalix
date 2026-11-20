package dev.portalix.mixin.gun;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.PortalixMod;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class GunOffhandRestrictionMixin {
  @Unique private static final int PORTALIX_MAIN_HAND = 1;
  @Unique private static final int PORTALIX_OFF_HAND = 2;
  @Shadow public ServerPlayer player;
  @Unique private int portalix$pendingHandCorrections;

  @Inject(method = "tick", at = @At("TAIL"))
  private void portalix$correctHands(CallbackInfo ci) {
    int pending = portalix$pendingHandCorrections;
    portalix$pendingHandCorrections = 0;
    if ((pending & PORTALIX_MAIN_HAND) != 0)
      player.connection.send(
          new ClientboundSetPlayerInventoryPacket(
              player.getInventory().getSelectedSlot(), player.getMainHandItem().copy()));
    if ((pending & PORTALIX_OFF_HAND) != 0)
      player.connection.send(
          new ClientboundSetPlayerInventoryPacket(
              Inventory.SLOT_OFFHAND, player.getOffhandItem().copy()));
  }

  @WrapOperation(
      method = "handlePlayerAction",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;hasClientLoaded()Z"))
  private boolean portalix$swap(
      ServerGamePacketListenerImpl listener, Operation<Boolean> original,
      @Local(argsOnly = true) ServerboundPlayerActionPacket packet) {
    if (!original.call(listener)) return false;
    if (!player.isSpectator()
        && packet.getAction() == Action.SWAP_ITEM_WITH_OFFHAND
        && (PortalixMod.isPortalix(player.getMainHandItem())
            || PortalixMod.isPortalix(player.getOffhandItem()))) {
      portalix$pendingHandCorrections |= PORTALIX_MAIN_HAND | PORTALIX_OFF_HAND;
      return false;
    }
    return true;
  }

  @WrapOperation(
      method = "handleSetCreativeModeSlot",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/server/level/ServerPlayer;hasInfiniteMaterials()Z"),
      allow = 1)
  private boolean portalix$creative(
      ServerPlayer player, Operation<Boolean> original,
      @Local(argsOnly = true) ServerboundSetCreativeModeSlotPacket packet) {
    if (!original.call(player)) return false;
    if (packet.slotNum() == InventoryMenu.SHIELD_SLOT
        && PortalixMod.isPortalix(packet.itemStack())) {
      portalix$pendingHandCorrections |= PORTALIX_OFF_HAND;
      return false;
    }
    return true;
  }
}
