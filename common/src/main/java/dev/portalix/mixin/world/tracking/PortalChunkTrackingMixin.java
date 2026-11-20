package dev.portalix.mixin.world.tracking;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import dev.portalix.platform.PortalPlatform;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.server.PortalServerEvents;
import dev.portalix.world.chunk.ChunkDeliveryAccess;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
abstract class PortalChunkTrackingMixin {
  @Shadow @Final private ServerLevel level;

  @ModifyExpressionValue(method = "updateChunkTracking", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/server/level/ChunkMap;getPlayerViewDistance(Lnet/minecraft/server/level/ServerPlayer;)I"))
  private int portalix$delayFullView(int distance, ServerPlayer player) {
    return PlayerPortalVisibility.of(player).trackingDistance(level.getGameTime(), distance);
  }

  @WrapMethod(method = "move")
  private void portalix$move(ServerPlayer player, Operation<Void> original) {
    PortalServerEvents.observerMoved(player);
    try {
      original.call(player);
    } finally {
      PortalServerEvents.observerMoveFinished(player);
    }
  }

  @WrapMethod(method = "applyChunkTrackingView")
  private void portalix$tracking(
      ServerPlayer player, ChunkTrackingView next, Operation<Void> original) {
    if (player.level() != level || !(next instanceof ChunkTrackingView.Positioned positioned)) {
      original.call(player, next);
      return;
    }
    ((ChunkDeliveryAccess) player.connection.chunkSender).portalix$enterLevel(player);
    PortalServerEvents.observerMoved(player, positioned.viewDistance());
    try {
      original.call(player, next);
    } finally {
      PortalServerEvents.observerMoveFinished(player);
    }
  }

  @Inject(
      method =
          "markChunkPendingToSend(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/ChunkPos;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void portalix$alreadyDelivered(
      ServerPlayer player, ChunkPos pos, CallbackInfo callback) {
    if (PortalServerEvents.retainedDuringMove(player, pos.pack())) callback.cancel();
  }

  @Inject(
      method =
          "markChunkPendingToSend(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/chunk/LevelChunk;)V",
      at = @At("HEAD"),
      cancellable = true)
  private static void portalix$promote(
      ServerPlayer player, LevelChunk chunk, CallbackInfo callback,
      @Share("restoreWatch") LocalBooleanRef restored) {
    long key = chunk.getPos().pack();
    var delivery = (ChunkDeliveryAccess) player.connection.chunkSender;
    var level = (ServerLevel) chunk.getLevel();
    if (player.level() != level) {
      callback.cancel();
      return;
    }
    delivery.portalix$enterLevel(player);
    if (delivery.portalix$watching(level, key)) {
      callback.cancel();
    } else if (delivery.portalix$subscribed(level, key)) {
      // This subscription is still active. Queue the refresh without a second Watch event.
      player.connection.chunkSender.markChunkPendingToSend(chunk);
      callback.cancel();
    } else {
      restored.set(delivery.portalix$delivered(level, key));
    }
  }

  @Inject(
      method =
          "markChunkPendingToSend(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/chunk/LevelChunk;)V",
      at = @At("TAIL"))
  private static void portalix$restoreWatch(
      ServerPlayer player, LevelChunk chunk, CallbackInfo callback,
      @Share("restoreWatch") LocalBooleanRef restored) {
    var delivery = (ChunkDeliveryAccess) player.connection.chunkSender;
    if (restored.get() && delivery.portalix$watching(player.level(), chunk.getPos().pack()))
      PortalPlatform.INSTANCE.resumeChunkWatch(player, chunk);
  }

  @WrapOperation(method = "onChunkReadyToSend", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/server/level/ChunkTrackingView;contains(Lnet/minecraft/world/level/ChunkPos;)Z"),
      require = 1, allow = 1)
  private boolean portalix$readyForPortal(ChunkTrackingView primary, ChunkPos pos,
      Operation<Boolean> original, @Local ServerPlayer player) {
    return original.call(primary, pos)
        || player.level() == level && PlayerPortalVisibility.of(player).contains(pos.pack());
  }

  @Inject(method = "isChunkTracked", at = @At("HEAD"), cancellable = true)
  private void portalix$tracked(
      ServerPlayer player, int x, int z, CallbackInfoReturnable<Boolean> callback) {
    long chunk = ChunkPos.pack(x, z);
    if (((ChunkDeliveryAccess) player.connection.chunkSender).portalix$delivered(level, chunk))
      callback.setReturnValue(true);
    else if (PlayerPortalVisibility.of(player).contains(x, z)) callback.setReturnValue(false);
  }

  @Inject(method = "dropChunk", at = @At("HEAD"), cancellable = true)
  private static void portalix$retain(ServerPlayer player, ChunkPos pos, CallbackInfo callback) {
    long key = pos.pack();
    var delivery = (ChunkDeliveryAccess) player.connection.chunkSender;
    if (!delivery.portalix$subscribed(player.level(), key)
        || PlayerPortalVisibility.of(player).contains(key) || PortalServerEvents.retainsTerrain(player, key))
      callback.cancel();
  }
}
