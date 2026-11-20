package dev.portalix.mixin.projectile;

import dev.portalix.portal.traversal.PortalProjectileRotation;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.joml.Quaternionfc;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArrow.class)
abstract class ArrowPortalRotationMixin implements PortalProjectileRotation.Access {
  @Unique private @Nullable Quaternionfc portalix$modelRotation;

  @Override
  public @Nullable Quaternionfc portalix$projectileModelRotation() {
    return portalix$modelRotation;
  }

  @Override
  public void portalix$projectileModelRotation(@Nullable Quaternionfc rotation) {
    portalix$modelRotation = rotation;
  }

  @Inject(method = "addAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueOutput;)V",
      at = @At("TAIL"), require = 1, allow = 1)
  private void portalix$saveModelRotation(ValueOutput output, CallbackInfo callback) {
    if (portalix$modelRotation != null)
      output.store("PortalProjectileModelRotation", PortalProjectileRotation.CODEC, portalix$modelRotation);
  }

  @Inject(method = "readAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueInput;)V",
      at = @At("TAIL"), require = 1, allow = 1)
  private void portalix$loadModelRotation(ValueInput input, CallbackInfo callback) {
    portalix$modelRotation = input.read("PortalProjectileModelRotation", PortalProjectileRotation.CODEC).orElse(null);
  }
}
