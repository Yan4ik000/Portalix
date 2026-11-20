package dev.portalix.mixin.traversal;

import dev.portalix.portal.traversal.PortalItemRotation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemPortalRotationMixin {
  @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
  private void portalix$saveRotation(ValueOutput output, CallbackInfo ci) {
    output.store("PortalRotation", ExtraCodecs.QUATERNIONF,
        ((PortalItemRotation.Access) this).portalix$rotation());
  }

  @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
  private void portalix$loadRotation(ValueInput input, CallbackInfo ci) {
    ((PortalItemRotation.Access) this).portalix$rotation(
        input.read("PortalRotation", ExtraCodecs.QUATERNIONF).orElseGet(Quaternionf::new));
  }
}
