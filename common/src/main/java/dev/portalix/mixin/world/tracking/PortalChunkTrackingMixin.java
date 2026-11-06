package dev.portalix.mixin.world.tracking;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.server.PortalServerEvents;
import dev.portalix.world.chunk.ChunkDeliveryAccess;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.FullChunkStatus;
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

  @Shadow
  protected abstract ChunkHolder getVisibleChunkIfPresent(long key);

  @WrapMethod(method = "move")
  private void portalix$move(ServerPlayer player, Operation<Void> original) {
    PortalServerEvents.observerMoved(player);
    original.call(player);
    PortalServerEvents.observerMoveFinished(player);
  }

  @WrapMethod(method = "applyChunkTrackingView")
  private void portalix$tracking(
      ServerPlayer player, ChunkTrackingView next, Operation<Void> original) {
    if (player.level() == level && next instanceof ChunkTrackingView.Positioned positioned)
      PortalServerEvents.observerMoved(player, positioned.viewDistance());
    original.call(player, next);
    if (player.level() == level && next instanceof ChunkTrackingView.Positioned)
      PortalServerEvents.observerMoveFinished(player);
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
      ServerPlayer player, LevelChunk chunk, CallbackInfo callback) {
    if (PortalServerEvents.retainedDuringMove(player, chunk.getPos().pack())) callback.cancel();
  }

  @Inject(method = "isChunkTracked", at = @At("HEAD"), cancellable = true)
  private void portalix$tracked(
      ServerPlayer player, int x, int z, CallbackInfoReturnable<Boolean> callback) {
    long chunk = ChunkPos.pack(x, z);
    if (((ChunkDeliveryAccess) player.connection.chunkSender).portalix$delivered(level, chunk))
      callback.setReturnValue(true);
    else if (PlayerPortalVisibility.of(player).contains(x, z)) callback.setReturnValue(false);
  }

  @Inject(method = "onFullChunkStatusChange", at = @At("HEAD"))
  private void portalix$lostUpdates(ChunkPos pos, FullChunkStatus status, CallbackInfo ci) {
    if (status.isOrAfter(FullChunkStatus.BLOCK_TICKING)) return;
    var holder = getVisibleChunkIfPresent(pos.pack());
    // Promotion callbacks can arrive after the ticking future has already completed.
    if (holder != null && holder.getTickingChunk() != null) return;
    // Below BLOCK_TICKING, ChunkHolder stops collecting block and light changes.
    for (var player : level.players())
      ((ChunkDeliveryAccess) player.connection.chunkSender).portalix$invalidate(level, pos.pack());
  }

  @Inject(method = "dropChunk", at = @At("HEAD"), cancellable = true)
  private static void portalix$retain(ServerPlayer player, ChunkPos pos, CallbackInfo callback) {
    if (PlayerPortalVisibility.of(player).contains(pos.x(), pos.z())) callback.cancel();
  }
}
