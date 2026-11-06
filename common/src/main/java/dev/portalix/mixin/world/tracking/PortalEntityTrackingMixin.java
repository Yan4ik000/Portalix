package dev.portalix.mixin.world.tracking;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.server.PortalEntityTracking;
import java.util.Set;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
abstract class PortalEntityTrackingMixin implements PortalEntityTracking.Tracker {
  @Shadow @Final private Entity entity;
  @Shadow @Final private Set<ServerPlayerConnection> seenBy;

  @Shadow public abstract void updatePlayer(ServerPlayer player);

  @Override
  @Unique
  public boolean portalix$hasObserver(ServerPlayer observer) {
    return seenBy.contains(observer.connection);
  }

  @WrapOperation(
      method = "updatePlayer(Lnet/minecraft/server/level/ServerPlayer;)V",
      at = @At(value = "INVOKE",
          target = "Lnet/minecraft/server/level/ServerPlayer;position()Lnet/minecraft/world/phys/Vec3;"),
      require = 1, allow = 1)
  private Vec3 portalix$observer(ServerPlayer player, Operation<Vec3> original) {
    Vec3 source = entity.position();
    Vec3 position = original.call(player);
    if (PortalEntityTracking.requiredOwner(entity, player)) return source;
    return source.add(PlayerPortalVisibility.of(player).nearestDelta(source, position.subtract(source)));
  }

  @WrapOperation(
      method = "updatePlayer(Lnet/minecraft/server/level/ServerPlayer;)V",
      at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/entity/Entity;broadcastToPlayer(Lnet/minecraft/server/level/ServerPlayer;)Z"),
      require = 1, allow = 1)
  private boolean portalix$hookOwner(Entity entity, ServerPlayer player, Operation<Boolean> original) {
    return original.call(entity, player) && PortalEntityTracking.ownerAvailable(entity, player);
  }

  @WrapOperation(
      method = "updatePlayer(Lnet/minecraft/server/level/ServerPlayer;)V",
      at = @At(value = "INVOKE",
          target = "Lnet/minecraft/server/level/ChunkMap;isChunkTracked(Lnet/minecraft/server/level/ServerPlayer;II)Z"),
      require = 1, allow = 1)
  private boolean portalix$ownerChunk(ChunkMap chunks, ServerPlayer player, int x, int z,
      Operation<Boolean> original) {
    return original.call(chunks, player, x, z) || PortalEntityTracking.requiredOwner(entity, player);
  }

  @WrapOperation(
      method = "updatePlayer(Lnet/minecraft/server/level/ServerPlayer;)V",
      at = @At(value = "INVOKE",
          target = "Lnet/minecraft/server/level/ServerEntity;addPairing(Lnet/minecraft/server/level/ServerPlayer;)V"),
      require = 1, allow = 1)
  private void portalix$ownerFirst(ServerEntity sender, ServerPlayer player, Operation<Void> original) {
    PortalEntityTracking.updateOwner(entity, player);
    original.call(sender, player);
  }

  @WrapOperation(
      method = {
          "removePlayer(Lnet/minecraft/server/level/ServerPlayer;)V",
          "broadcastRemoved()V"
      },
      at = @At(value = "INVOKE",
          target = "Lnet/minecraft/server/level/ServerEntity;removePairing(Lnet/minecraft/server/level/ServerPlayer;)V"),
      require = 2, allow = 2)
  private void portalix$ownerAfterHook(ServerEntity sender, ServerPlayer player, Operation<Void> original) {
    original.call(sender, player);
    PortalEntityTracking.updateOwner(entity, player);
  }
}
