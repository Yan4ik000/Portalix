package dev.portalix.mixin.traversal;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.traversal.PortalCollisionWorld;
import dev.portalix.portal.traversal.PortalEntityCollisions;
import dev.portalix.portal.traversal.PortalEntityMovement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityPortalMovementMixin implements EntityPortalState.Access {
  @Unique private final EntityPortalState portalix$motion = new EntityPortalState();

  @Override
  public EntityPortalState portalix$motionState() {
    return portalix$motion;
  }

  @Inject(method = "move", at = @At("HEAD"), cancellable = true)
  private void portalix$move(MoverType type, Vec3 displacement, CallbackInfo callback) {
    if (PortalEntityMovement.move((Entity) (Object) this, type, displacement)) callback.cancel();
  }

  @WrapOperation(
      method = "move",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;limitPistonMovement(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"),
      require = 1, allow = 1)
  private Vec3 portalix$pistonSegment(Entity entity, Vec3 movement, Operation<Vec3> original) {
    return portalix$motion.segmentMoving() ? movement : original.call(entity, movement);
  }

  @Inject(method = "clearMovementThisTick", at = @At("HEAD"), require = 1, allow = 1)
  private void portalix$movementRecordsCleared(CallbackInfo ci) {
    portalix$motion.clearedMovementRecords();
  }

  @Inject(method = "applyEffectsFromBlocks()V", at = @At(value = "INVOKE",
      target = "Ljava/util/ArrayDeque;clear()V", shift = At.Shift.AFTER),
      require = 1, allow = 1)
  private void portalix$movementRecordsConsumed(CallbackInfo ci) {
    portalix$motion.clearedMovementRecords();
  }

  @Definition(id = "movementLength", local = @Local(type = double.class, name = "movementLength"))
  @Expression("movementLength > ?")
  @WrapOperation(
      method = "move",
      at = @At("MIXINEXTRAS:EXPRESSION"),
      slice =
          @Slice(
              from =
                  @At(
                      value = "INVOKE",
                      target =
                          "Lnet/minecraft/world/entity/Entity;collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"),
              to =
                  @At(
                      value = "INVOKE",
                      target =
                          "Lnet/minecraft/world/entity/Entity;position()Lnet/minecraft/world/phys/Vec3;")),
      require = 1,
      allow = 1)
  private boolean portalix$segmentMovementThreshold(
      double length, double threshold, Operation<Boolean> original) {
    return original.call(length, portalix$motion.segmentMoving() ? 0.0 : threshold);
  }

  @Inject(method = "collide", at = @At("HEAD"), cancellable = true)
  private void portalix$collide(Vec3 displacement, CallbackInfoReturnable<Vec3> callback) {
    Vec3 collision = PortalEntityCollisions.collide((Entity) (Object) this, displacement);
    if (collision != null) callback.setReturnValue(collision);
  }

  @Inject(method = "checkFallDamage", at = @At("HEAD"), cancellable = true)
  private void portalix$fall(
      double movement, boolean grounded, BlockState block, BlockPos pos, CallbackInfo callback) {
    if (PortalEntityCollisions.suppressFallDamage((Entity) (Object) this, grounded))
      callback.cancel();
  }

  @Inject(method = "isInWall", at = @At("HEAD"), cancellable = true)
  private void portalix$suffocation(CallbackInfoReturnable<Boolean> callback) {
    Boolean collision = PortalEntityCollisions.isInWall((Entity) (Object) this);
    if (collision != null) callback.setReturnValue(collision);
  }

  @Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
  private void portalix$pushOut(double x, double y, double z, CallbackInfo callback) {
    Entity entity = (Entity) (Object) this;
    if (PortalTopology.links(entity.level()).isEmpty()) return;
    var hull = entity.getBoundingBox().deflate(1e-7);
    var link = PortalEntityCollisions.intersectingPortal(entity, hull, Vec3.ZERO);
    if (link != null && !PortalCollisionWorld.query(entity, link, hull).intersects(hull))
      callback.cancel();
  }
}
