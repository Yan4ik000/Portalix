package dev.portalix.mixin.projectile;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.portal.geometry.PortalTransform;
import dev.portalix.portal.traversal.ProjectilePortalMovement;
import java.util.function.Predicate;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ShulkerBullet.class)
public abstract class ShulkerPortalRayMixin implements ProjectilePortalMovement.Guided {
  @Shadow private double targetDeltaX;
  @Shadow private double targetDeltaY;
  @Shadow private double targetDeltaZ;
  @Shadow private Direction currentMoveDirection;

  @Override
  public void portalix$transformGuidance(PortalTransform transform) {
    Vec3 target = transform.vector(new Vec3(targetDeltaX, targetDeltaY, targetDeltaZ));
    targetDeltaX = target.x;
    targetDeltaY = target.y;
    targetDeltaZ = target.z;
    if (currentMoveDirection != null)
      currentMoveDirection =
          Direction.getApproximateNearest(
              transform.vector(Vec3.atLowerCornerOf(currentMoveDirection.getUnitVec3i())));
  }

  @WrapOperation(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getHitResultOnMoveVector(Lnet/minecraft/world/entity/Entity;Ljava/util/function/Predicate;)Lnet/minecraft/world/phys/HitResult;"))
  private HitResult portalix$ray(
      Entity entity, Predicate<Entity> targets, Operation<HitResult> original) {
    return ProjectilePortalMovement.trace(
        entity, targets, ClipContext.Block.COLLIDER, () -> original.call(entity, targets));
  }

  @ModifyArg(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/projectile/ShulkerBullet;setPos(Lnet/minecraft/world/phys/Vec3;)V"))
  private Vec3 portalix$position(Vec3 original) {
    return ProjectilePortalMovement.position((Entity) (Object) this, original);
  }
}
