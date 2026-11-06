package dev.portalix.item;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class PortalixItem extends Item {
  private final PortalixVariant variant;

  public PortalixItem(Properties properties, PortalixVariant variant) {
    super(properties);
    this.variant = variant;
  }

  public PortalixVariant variant() {
    return variant;
  }

  @Override
  public boolean canDestroyBlock(
      ItemStack stack, BlockState state, Level level, BlockPos pos, LivingEntity user) {
    return false;
  }

  @Override
  public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
    PortalixIdentity.ensure(stack);
    if (slot == EquipmentSlot.OFFHAND && entity instanceof Player player) {
      player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
      if (!player.getInventory().add(stack)) {
        player.drop(stack, false);
      }
    }
  }
}
