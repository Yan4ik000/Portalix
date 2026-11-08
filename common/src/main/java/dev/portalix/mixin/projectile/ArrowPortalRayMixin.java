package dev.portalix.mixin.projectile;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.portal.query.PortalRaycast;
import dev.portalix.portal.traversal.ProjectilePortalMovement;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractArrow.class)
public abstract class ArrowPortalRayMixin {
  @WrapOperation(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/projectile/arrow/AbstractArrow;stepMoveAndHit(Lnet/minecraft/world/phys/BlockHitResult;)V"),
      allow = 1)
  private void portalix$segments(
      AbstractArrow arrow, BlockHitResult hit, Operation<Void> original,
      @Local(ordinal = 0) Vec3 movement, @Local(ordinal = 1) Vec3 from) {
    var trace = PortalRaycast.castArrow(arrow, from, movement, hit);
    if (trace == null) {
      original.call(arrow, hit);
      return;
    }
    ProjectilePortalMovement.moveArrow(
        arrow, trace, movement.length(), segment -> original.call(arrow, segment));
  }
}
