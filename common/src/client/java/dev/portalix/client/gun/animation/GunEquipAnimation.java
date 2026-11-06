package dev.portalix.client.gun.animation;

import dev.portalix.PortalixMod;
import dev.portalix.item.PortalixIdentity;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;

public final class GunEquipAnimation {
  private final GunAnimator animator;
  private ItemStack displayed = ItemStack.EMPTY;
  private int slot = -1;

  public GunEquipAnimation(GunAnimator animator) {
    this.animator = animator;
  }

  public ItemStack displayed() {
    return displayed;
  }

  public GunAnimationClip tick(ItemStack selected, int selectedSlot) {
    if (animator.isHolstering()) {
      if (!animator.isHolstered()) return null;
      displayed = ItemStack.EMPTY;
      animator.setEquipped(false);
    }
    if (!displayed.isEmpty() && !matches(selected, selectedSlot)) {
      animator.holster();
      return GunAnimationClip.HOLSTER;
    }
    if (PortalixMod.isPortalix(selected)) {
      boolean draw = displayed.isEmpty();
      displayed = selected.copy();
      slot = selectedSlot;
      if (draw) {
        animator.setEquipped(true);
        animator.play(GunAnimationClip.DRAW);
        return GunAnimationClip.DRAW;
      }
    }
    return null;
  }

  private boolean matches(ItemStack selected, int selectedSlot) {
    if (slot != selectedSlot || displayed.getItem() != selected.getItem()) return false;
    var previous = PortalixIdentity.get(displayed);
    var current = PortalixIdentity.get(selected);
    return previous == null || current == null || Objects.equals(previous, current);
  }
}
