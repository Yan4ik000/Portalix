package dev.portalix.mixin.projectile;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.portal.query.PortalRaycast;
import dev.portalix.portal.traversal.ProjectilePortalMovement;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArrow.class)
public abstract class ArrowPortalRayMixin {
  @Unique private Vec3 portalix$movement;

  @Inject(method = "tick", at = @At("HEAD"))
  private void portalix$capture(CallbackInfo callback) {
    portalix$movement = ((AbstractArrow) (Object) this).getDeltaMovement();
  }

  @WrapOperation(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/projectile/arrow/AbstractArrow;stepMoveAndHit(Lnet/minecraft/world/phys/BlockHitResult;)V"),
      allow = 1)
  private void portalix$segments(
      AbstractArrow arrow, BlockHitResult hit, Operation<Void> original) {
    var trace =
        PortalRaycast.cast(
            arrow,
            arrow.position(),
            portalix$movement,
            target -> false,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            0);
    if (trace.links().isEmpty()) {
      original.call(arrow, hit);
      return;
    }
    ProjectilePortalMovement.moveArrow(
        arrow, trace, portalix$movement.length(), segment -> original.call(arrow, segment));
  }
}
