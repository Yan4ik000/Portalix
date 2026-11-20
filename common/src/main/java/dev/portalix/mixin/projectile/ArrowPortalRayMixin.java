package dev.portalix.mixin.projectile;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.portalix.portal.query.PortalRaycast;
import dev.portalix.portal.traversal.ProjectilePortalMovement;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractArrow.class)
public abstract class ArrowPortalRayMixin {
  @WrapOperation(
      method = "tick",
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/world/level/Level;clipIncludingBorder(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"),
      require = 1, allow = 1)
  private BlockHitResult portalix$captureRay(
      Level level, ClipContext context, Operation<BlockHitResult> original,
      @Share("ray") LocalRef<ClipContext> ray) {
    ray.set(context);
    return original.call(level, context);
  }

  @WrapOperation(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/projectile/arrow/AbstractArrow;stepMoveAndHit(Lnet/minecraft/world/phys/BlockHitResult;)V"),
      require = 1, allow = 1)
  private void portalix$segments(
      AbstractArrow arrow, BlockHitResult hit, Operation<Void> original,
      @Share("ray") LocalRef<ClipContext> ray) {
    var context = ray.get();
    Vec3 from = context.getFrom();
    Vec3 movement = context.getTo().subtract(from);
    var trace = PortalRaycast.castArrow(arrow, from, movement, hit);
    if (trace == null) {
      original.call(arrow, hit);
      return;
    }
    ProjectilePortalMovement.moveArrow(
        arrow, trace, movement.length(), segment -> original.call(arrow, segment));
  }
}
