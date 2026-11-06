package dev.portalix.mixin.traversal;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.traversal.PortalCollisionWorld;
import dev.portalix.portal.traversal.PortalEntityCollisions;
import dev.portalix.portal.traversal.PortalPassagePose;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerPortalPoseMixin {
  @Inject(method = "tick", at = @At("TAIL"))
  private void portalix$recoverPose(CallbackInfo ci) {
    Player player = (Player) (Object) this;
    if (!(player instanceof ServerPlayer)) PortalPassagePose.tick(player);
  }

  @WrapOperation(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/entity/player/Player;updatePlayerPose()V"))
  private void portalix$deferServerPose(Player player, Operation<Void> original) {
    if (!(player instanceof ServerPlayer)) original.call(player);
  }

  @WrapOperation(
      method = "canPlayerFitWithinBlocksAndEntitiesWhen",
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"))
  private boolean portalix$pose(
      Level level, Entity player, AABB body, Operation<Boolean> original) {
    Boolean portal = PortalPassagePose.fitsThroughPortal(player, body);
    return portal != null ? portal : original.call(level, player, body);
  }

  @Inject(method = "maybeBackOffFromEdge", at = @At("HEAD"), cancellable = true)
  private void portalix$segmentEdge(
      Vec3 movement, MoverType mover, CallbackInfoReturnable<Vec3> callback) {
    Player player = (Player) (Object) this;
    if (EntityPortalState.of(player).segmentMoving()) {
      callback.setReturnValue(movement);
      return;
    }
    var access = (PlayerAccessor) player;
    float depth = player.maxUpStep();
    if (player.getAbilities().flying
        || movement.y > 0
        || mover != MoverType.SELF && mover != MoverType.PLAYER
        || !access.portalix$stayingOnGroundSurface()
        || !access.portalix$aboveGround(depth)) return;
    var body = player.getBoundingBox();
    var query = body.expandTowards(movement.x, -depth - 1e-7, movement.z);
    var link = PortalEntityCollisions.intersectingPortal(player, query, Vec3.ZERO);
    if (link != null)
      callback.setReturnValue(
          player.isFallFlying()
              ? movement
              : PortalCollisionWorld.queryGroundSupport(player, link, query, body, depth)
                  .outsideOpening(link.entrance().endpoint(), body, depth)
                  .backOff(movement));
  }

  @Inject(method = "canFallAtLeast", at = @At("HEAD"), cancellable = true)
  private void portalix$support(
      double x, double z, double depth, CallbackInfoReturnable<Boolean> callback) {
    Player player = (Player) (Object) this;
    var body = player.getBoundingBox();
    var query = body.expandTowards(x, -depth - 1e-7, z);
    var link = PortalEntityCollisions.intersectingPortal(player, query, Vec3.ZERO);
    if (link != null)
      callback.setReturnValue(
          !PortalCollisionWorld.queryGroundSupport(player, link, query, body, depth)
              .supports(new Vec3(x, 0, z)));
  }
}
