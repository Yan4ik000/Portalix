package dev.portalix.mixin.projectile;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.portal.traversal.ProjectilePortalMovement;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(AbstractHurtingProjectile.class)
public abstract class FireballPortalRayMixin {
  @WrapOperation(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getHitResultOnMoveVector(Lnet/minecraft/world/entity/Entity;Ljava/util/function/Predicate;Lnet/minecraft/world/level/ClipContext$Block;)Lnet/minecraft/world/phys/HitResult;"))
  private HitResult portalix$ray(
      Entity entity,
      Predicate<Entity> targets,
      ClipContext.Block blocks,
      Operation<HitResult> original) {
    return ProjectilePortalMovement.trace(
        entity, targets, blocks, () -> original.call(entity, targets, blocks));
  }

  @ModifyArg(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/projectile/hurtingprojectile/AbstractHurtingProjectile;setPos(Lnet/minecraft/world/phys/Vec3;)V"))
  private Vec3 portalix$position(Vec3 original) {
    return ProjectilePortalMovement.position((Entity) (Object) this, original);
  }
}
