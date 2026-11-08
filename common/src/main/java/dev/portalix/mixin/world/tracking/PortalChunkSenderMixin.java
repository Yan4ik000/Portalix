package dev.portalix.mixin.world.tracking;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.server.PortalEntityTracking;
import dev.portalix.server.PortalServerEvents;
import dev.portalix.world.chunk.PortalChunkObservers;
import dev.portalix.world.chunk.ChunkDeliveryAccess;
import dev.portalix.world.chunk.ChunkDeliveryQueue;
import dev.portalix.world.chunk.ChunkSendQueue;
import dev.portalix.world.snapshot.LocalChunkPalettes;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerChunkSender.class)
abstract class PortalChunkSenderMixin implements ChunkDeliveryAccess {
  @Shadow @Final private LongSet pendingChunks;
  @Shadow @Final private boolean memoryConnection;
  @Shadow private float batchQuota;
  @Unique private final ChunkDeliveryQueue portalix$delivery = new ChunkDeliveryQueue();
  @Unique private ServerLevel portalix$level;
  @Unique private PortalChunkObservers portalix$observers = PortalChunkObservers.EMPTY;
  @Unique private ChunkDeliveryQueue.Batch portalix$batch;

  @Inject(method = "markChunkPendingToSend", at = @At("HEAD"), cancellable = true)
  private void portalix$dimension(LevelChunk chunk, CallbackInfo ci) {
    portalix$enterLevel((ServerLevel) chunk.getLevel());
    if (portalix$delivery.requested(chunk.getPos().pack())) {
      pendingChunks.remove(chunk.getPos().pack());
      ci.cancel();
    }
  }

  @Unique
  private void portalix$enterLevel(ServerLevel level) {
    if (portalix$level == level) return;
    portalix$level = level;
    portalix$delivery.clear();
    pendingChunks.clear();
  }

  @Inject(method = "sendChunk", at = @At("TAIL"))
  private static void portalix$sent(
      ServerGamePacketListenerImpl connection,
      ServerLevel level,
      LevelChunk chunk,
      CallbackInfo ci) {
    var sender = (PortalChunkSenderMixin) (Object) connection.chunkSender;
    long key = chunk.getPos().pack();
    sender.portalix$delivery.sent(key);
    PortalServerEvents.chunkDeliveryChanged(connection.player, key);
  }

  @Inject(method = "dropChunk", at = @At("HEAD"), cancellable = true)
  private void portalix$forgotten(ServerPlayer player, ChunkPos pos, CallbackInfo ci) {
    portalix$enterLevel(player.level());
    pendingChunks.remove(pos.pack());
    portalix$delivery.retire(pos.pack());
    ci.cancel();
  }

  @Override
  public boolean portalix$watching(ServerLevel level, long chunk) {
    return portalix$level == level
        && (pendingChunks.contains(chunk) || portalix$delivery.watching(chunk));
  }

  @Override
  public boolean portalix$delivered(ServerLevel level, long chunk) {
    return portalix$level == level
        && portalix$delivery.delivered(chunk)
        && !pendingChunks.contains(chunk);
  }

  @Override
  public LongSet portalix$presentColumns(ServerLevel level) {
    return portalix$level == level ? portalix$delivery.presentColumns() : LongSets.EMPTY_SET;
  }

  @Override
  public boolean portalix$retain(ServerLevel level, long chunk) {
    return portalix$level == level && portalix$delivery.requested(chunk);
  }

  @Override
  public void portalix$invalidate(ServerLevel level, long chunk) {
    if (portalix$level == level) portalix$delivery.invalidate(chunk);
  }

  @WrapMethod(method = "sendNextChunks")
  private void portalix$observers(ServerPlayer player, Operation<Void> original) {
    portalix$enterLevel(player.level());
    if (portalix$delivery.hasRetirements() && !player.isAlive()) return;
    if (pendingChunks.isEmpty() && !portalix$delivery.hasRetirements()) {
      original.call(player);
      return;
    }
    try {
      if (memoryConnection) LocalChunkPalettes.send(true, () -> original.call(player));
      else original.call(player);
    } finally {
      portalix$batch = null;
      portalix$observers = PortalChunkObservers.EMPTY;
    }
  }

  @WrapOperation(method = "sendNextChunks", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/server/network/PlayerChunkSender;collectChunksToSend(Lnet/minecraft/server/level/ChunkMap;Lnet/minecraft/world/level/ChunkPos;)Ljava/util/List;"),
      require = 1, allow = 1)
  private List<LevelChunk> portalix$prioritizeBatch(PlayerChunkSender sender, ChunkMap map,
      ChunkPos center, Operation<List<LevelChunk>> original, @Local(argsOnly = true) ServerPlayer player) {
    portalix$observers = PortalChunkObservers.of(player);
    return original.call(sender, map, center);
  }

  @WrapOperation(
      method = "sendNextChunks",
      at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/LongSet;isEmpty()Z"),
      allow = 1)
  private boolean portalix$pending(LongSet pending, Operation<Boolean> original) {
    return original.call(pending) && !portalix$delivery.hasRetirements();
  }

  @WrapOperation(
      method = "sendNextChunks",
      at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z"),
      allow = 1)
  private boolean portalix$emptyBatch(List<?> loaded, Operation<Boolean> original) {
    return original.call(loaded) && (portalix$batch == null || portalix$batch.size() == 0);
  }

  @WrapOperation(
      method = "sendNextChunks",
      at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"),
      require = 2, allow = 2)
  private int portalix$batchSize(List<?> loaded, Operation<Integer> original) {
    return portalix$batch == null ? original.call(loaded) : portalix$batch.size();
  }

  @WrapOperation(
      method = "sendNextChunks",
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"),
      require = 2, allow = 2)
  private void portalix$trackBatch(
      ServerGamePacketListenerImpl connection, Packet<?> packet, Operation<Void> original,
      @Local List<LevelChunk> loaded) {
    original.call(connection, packet);
    if (packet instanceof ClientboundChunkBatchFinishedPacket)
      PortalEntityTracking.refreshDeliveredColumns(connection.player, loaded);
    if (packet instanceof ClientboundChunkBatchStartPacket && portalix$batch != null) {
      var visibility = PlayerPortalVisibility.of(connection.player);
      for (long chunk : portalix$batch.forgotten()) {
        visibility.forgetColumn(chunk);
        connection.send(new ClientboundForgetLevelChunkPacket(ChunkPos.unpack(chunk)));
        PortalServerEvents.chunkDeliveryChanged(connection.player, chunk);
      }
    }
  }

  @Inject(method = "collectChunksToSend", at = @At("HEAD"), cancellable = true)
  private void portalix$visibleFirst(
      ChunkMap map, ChunkPos player, CallbackInfoReturnable<List<LevelChunk>> cir) {
    if (portalix$observers.isEmpty() && !portalix$delivery.hasRetirements()) return;
    var selected =
        ChunkSendQueue.nearest(
            pendingChunks,
            Mth.floor(batchQuota),
            portalix$observers::priority,
            key -> map.getChunkToSend(key) != null);
    if (portalix$delivery.hasRetirements()) {
      portalix$batch = portalix$delivery.select(
          selected, Mth.floor(batchQuota), portalix$observers::distanceSquared);
      selected = portalix$batch.loaded();
    }
    var chunks = new ArrayList<LevelChunk>(selected.length);
    for (long key : selected) {
      var chunk = map.getChunkToSend(key);
      if (chunk != null) {
        pendingChunks.remove(key);
        chunks.add(chunk);
      }
    }
    cir.setReturnValue(chunks);
  }

}
