package dev.portalix.fabric.mixin;

import dev.portalix.portal.traversal.PortalItemRotation;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.item.ItemEntity;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
abstract class ItemPortalDataMixin implements PortalItemRotation.Access {
  @Unique private static final EntityDataAccessor<Quaternionfc> PORTALIX_ROTATION =
      SynchedEntityData.defineId(ItemEntity.class, EntityDataSerializers.QUATERNION);

  @Inject(method = "defineSynchedData", at = @At("TAIL"))
  private void definePortalRotation(SynchedEntityData.Builder builder, CallbackInfo callback) {
    builder.define(PORTALIX_ROTATION, new Quaternionf());
  }

  @Override
  public Quaternionfc portalix$rotation() {
    return ((ItemEntity) (Object) this).getEntityData().get(PORTALIX_ROTATION);
  }

  @Override
  public void portalix$rotation(Quaternionfc rotation) {
    ((ItemEntity) (Object) this).getEntityData().set(PORTALIX_ROTATION, rotation);
  }
}
