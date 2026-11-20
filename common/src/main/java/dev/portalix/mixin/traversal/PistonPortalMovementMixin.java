package dev.portalix.mixin.traversal;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.portalix.compat.minecraft.EntityMotion;
import dev.portalix.portal.traversal.PortalEntityMovement;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PistonMovingBlockEntity.class)
abstract class PistonPortalMovementMixin {
  @WrapOperation(method = "moveEntityByPiston", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/world/entity/Entity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"),
      require = 1, allow = 1)
  private static void portalix$captureMovement(Entity entity, MoverType type, Vec3 movement,
      Operation<Void> original,
      @Share("piston") LocalRef<EntityMotion.PistonMovement> piston) {
    if (PortalEntityMovement.canReplay(entity, Vec3.ZERO, movement))
      piston.set(new EntityMotion.PistonMovement(entity));
    original.call(entity, type, movement);
  }

  @WrapOperation(method = "moveEntityByPiston", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/world/entity/Entity;applyEffectsFromBlocks(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)V"),
      require = 1, allow = 1)
  private static void portalix$applyMovementEffects(Entity entity, Vec3 from, Vec3 to,
      Operation<Void> original,
      @Share("piston") LocalRef<EntityMotion.PistonMovement> piston,
      @Share("applied") LocalBooleanRef applied) {
    var motion = piston.get();
    if (motion == null || !motion.applyEffects(entity, from, to))
      original.call(entity, from, to);
    else applied.set(true);
  }

  @WrapOperation(method = "moveEntityByPiston", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/world/entity/Entity;removeLatestMovementRecording()V"),
      require = 1, allow = 1)
  private static void portalix$removeMovementRecords(Entity entity, Operation<Void> original,
      @Share("applied") LocalBooleanRef applied) {
    if (!applied.get()) original.call(entity);
  }
}
