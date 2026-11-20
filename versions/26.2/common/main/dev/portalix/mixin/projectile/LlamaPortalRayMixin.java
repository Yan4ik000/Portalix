package dev.portalix.mixin.projectile;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.portal.traversal.PortalCollisionWorld;
import dev.portalix.portal.traversal.PortalEntityCollisions;
import dev.portalix.portal.traversal.ProjectilePortalMovement;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LlamaSpit.class)
public abstract class LlamaPortalRayMixin {
  @WrapOperation(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target = "Ljava/util/stream/Stream;noneMatch(Ljava/util/function/Predicate;)Z"),
      allow = 1)
  private <T> boolean portalix$inside(
      Stream<T> states, Predicate<? super T> predicate, Operation<Boolean> original) {
    if (!original.call(states, predicate)) return false;
    Entity entity = (Entity) (Object) this;
    var link = PortalEntityCollisions.intersectingPortal(entity);
    return link == null
        || PortalCollisionWorld.query(entity, link, entity.getBoundingBox())
            .intersects(entity.getBoundingBox());
  }

  @WrapOperation(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getHitResultOnMoveVector(Lnet/minecraft/world/entity/Entity;Ljava/util/function/Predicate;)Lnet/minecraft/world/phys/HitResult;"),
      allow = 1)
  private HitResult portalix$ray(
      Entity entity, Predicate<Entity> targets, Operation<HitResult> original) {
    return ProjectilePortalMovement.trace(
        entity, targets, ClipContext.Block.COLLIDER, () -> original.call(entity, targets));
  }

  @ModifyArgs(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/entity/projectile/LlamaSpit;setPos(DDD)V"),
      allow = 1)
  private void portalix$position(Args args) {
    Vec3 position =
        ProjectilePortalMovement.position(
            (Entity) (Object) this, new Vec3(args.get(0), args.get(1), args.get(2)));
    args.set(0, position.x);
    args.set(1, position.y);
    args.set(2, position.z);
  }

  @ModifyArg(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/projectile/LlamaSpit;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"),
      allow = 1)
  private Vec3 portalix$velocity(Vec3 original) {
    return ProjectilePortalMovement.capturedVelocity((Entity) (Object) this, original);
  }
}
