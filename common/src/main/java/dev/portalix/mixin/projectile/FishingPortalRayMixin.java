package dev.portalix.mixin.projectile;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.portal.traversal.ProjectilePortalMovement;
import java.util.function.Predicate;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(FishingHook.class)
public abstract class FishingPortalRayMixin {
  @WrapOperation(
      method = "shouldStopFishing",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/projectile/FishingHook;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"))
  private double portalix$tetherDistance(
      FishingHook hook, Entity owner, Operation<Double> original) {
    return ProjectilePortalMovement.tetherDistance(hook, owner, original.call(hook, owner));
  }

  @WrapOperation(
      method = "pullEntity",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getX()D"))
  private double portalix$ownerX(Entity owner, Operation<Double> original) {
    return ProjectilePortalMovement.tetherCoordinate(
        (FishingHook) (Object) this, owner, Direction.Axis.X, original.call(owner));
  }

  @WrapOperation(
      method = "pullEntity",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getY()D"))
  private double portalix$ownerY(Entity owner, Operation<Double> original) {
    return ProjectilePortalMovement.tetherCoordinate(
        (FishingHook) (Object) this, owner, Direction.Axis.Y, original.call(owner));
  }

  @WrapOperation(
      method = "pullEntity",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getZ()D"))
  private double portalix$ownerZ(Entity owner, Operation<Double> original) {
    return ProjectilePortalMovement.tetherCoordinate(
        (FishingHook) (Object) this, owner, Direction.Axis.Z, original.call(owner));
  }

  @WrapOperation(
      method = "retrieve",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getX()D"),
      slice =
          @Slice(
              to =
                  @At(
                      value = "INVOKE",
                      target =
                          "Lnet/minecraft/world/entity/item/ItemEntity;setDeltaMovement(DDD)V")),
      allow = 1)
  private double portalix$lootOwnerX(Player owner, Operation<Double> original) {
    return ProjectilePortalMovement.tetherCoordinate(
        (FishingHook) (Object) this, owner, Direction.Axis.X, original.call(owner));
  }

  @WrapOperation(
      method = "retrieve",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getY()D"),
      slice =
          @Slice(
              to =
                  @At(
                      value = "INVOKE",
                      target =
                          "Lnet/minecraft/world/entity/item/ItemEntity;setDeltaMovement(DDD)V")),
      allow = 1)
  private double portalix$lootOwnerY(Player owner, Operation<Double> original) {
    return ProjectilePortalMovement.tetherCoordinate(
        (FishingHook) (Object) this, owner, Direction.Axis.Y, original.call(owner));
  }

  @WrapOperation(
      method = "retrieve",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getZ()D"),
      slice =
          @Slice(
              to =
                  @At(
                      value = "INVOKE",
                      target =
                          "Lnet/minecraft/world/entity/item/ItemEntity;setDeltaMovement(DDD)V")),
      allow = 1)
  private double portalix$lootOwnerZ(Player owner, Operation<Double> original) {
    return ProjectilePortalMovement.tetherCoordinate(
        (FishingHook) (Object) this, owner, Direction.Axis.Z, original.call(owner));
  }

  @WrapOperation(
      method = "checkCollision",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getHitResultOnMoveVector(Lnet/minecraft/world/entity/Entity;Ljava/util/function/Predicate;)Lnet/minecraft/world/phys/HitResult;"))
  private HitResult portalix$ray(
      Entity entity, Predicate<Entity> targets, Operation<HitResult> original) {
    return ProjectilePortalMovement.movementRay(
        entity, targets, () -> original.call(entity, targets));
  }

  @WrapOperation(
      method = "onHitBlock",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/phys/BlockHitResult;distanceTo(Lnet/minecraft/world/entity/Entity;)D"))
  private double portalix$distance(BlockHitResult hit, Entity entity, Operation<Double> original) {
    return ProjectilePortalMovement.hitDistance(entity, original.call(hit, entity));
  }
}
