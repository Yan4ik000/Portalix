package dev.portalix.neoforge.mixin;

import dev.portalix.neoforge.NeoForgePortalData;
import dev.portalix.portal.traversal.PortalItemRotation;
import net.minecraft.world.entity.item.ItemEntity;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemEntity.class)
abstract class ItemPortalDataMixin implements PortalItemRotation.Access {
  @Override
  public Quaternionfc portalix$rotation() {
    return NeoForgePortalData.itemRotation((ItemEntity) (Object) this);
  }

  @Override
  public void portalix$rotation(Quaternionfc rotation) {
    NeoForgePortalData.itemRotation((ItemEntity) (Object) this, rotation);
  }
}
