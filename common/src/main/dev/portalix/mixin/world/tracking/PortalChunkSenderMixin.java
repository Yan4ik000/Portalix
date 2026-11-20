package dev.portalix.mixin.world.tracking;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.platform.PortalPlatform;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.server.PortalEntityTracking;
import dev.portalix.server.PortalServerEvents;
import dev.portalix.server.portal.PortalChunkTracker;
import dev.portalix.world.chunk.ChunkDeliveryAccess;
import dev.portalix.world.chunk.ChunkDeliveryQueue;
import dev.portalix.world.chunk.ChunkSendQueue;
import dev.portalix.world.chunk.PortalChunkObservers;
import dev.portalix.world.snapshot.LocalChunkPalettes;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
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
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerChunkSender.class)
abstract class PortalChunkSenderMixin implements ChunkDeliveryAccess {
  @Shadow @Final private LongSet pendingChunks;
  @Shadow @Final private boolean memoryConnection;
  @Shadow private float batchQuota;
  @Shadow private float desiredChunksPerTick;
  @Unique private final ChunkDeliveryQueue portalix$delivery = new ChunkDeliveryQueue();
  @Unique private final Long2IntOpenHashMap portalix$refreshes = new Long2IntOpenHashMap();
  @Unique private final Int2ObjectOpenHashMap<LongOpenHashSet> portalix$refreshBatches =
      new Int2ObjectOpenHashMap<>();
  @Unique private ServerLevel portalix$level;
  @Unique private float portalix$retirementQuota;
  @Unique private boolean portalix$selected;

  @Inject(method = "markChunkPendingToSend", at = @At("HEAD"), cancellable = true)
  private void portalix$reuseDeliveredChunk(LevelChunk chunk, CallbackInfo ci) {
    if (portalix$level != chunk.getLevel()) return;
    long key = chunk.getPos().pack();
    if (portalix$delivery.requested(key)) {
      pendingChunks.remove(key);
      ci.cancel();
    }
  }

  @Override
  public void portalix$enterLevel(ServerPlayer player) {
    var level = player.level();
    if (portalix$level == level) return;
    portalix$leaveLevel(player);
    portalix$level = level;
  }

  @Override
  public void portalix$leaveLevel(ServerPlayer player) {
    var level = portalix$level;
    if (level == null) return;
    var platform = PortalPlatform.INSTANCE;
    // Native teardown drops the primary view. End any still-active portal subscriptions here.
    for (long key : pendingChunks) platform.endChunkWatch(player, level, ChunkPos.unpack(key));
    for (long key : portalix$delivery.presentColumns()) {
      if (portalix$delivery.subscribed(key) && !pendingChunks.contains(key))
        platform.endChunkWatch(player, level, ChunkPos.unpack(key));
    }
    portalix$level = null;
    portalix$delivery.clear();
    portalix$refreshes.clear();
    portalix$refreshBatches.clear();
    portalix$retirementQuota = 0;
    pendingChunks.clear();
  }

  @Inject(method = "sendChunk", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V",
      shift = At.Shift.AFTER), require = 1, allow = 1)
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
    pendingChunks.remove(pos.pack());
    portalix$delivery.retire(pos.pack());
    ci.cancel();
  }

  @Override
  public boolean portalix$subscribed(ServerLevel level, long chunk) {
    return portalix$level == level && (pendingChunks.contains(chunk) || portalix$delivery.subscribed(chunk));
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
  public boolean portalix$retain(ServerPlayer player, long chunk) {
    var level = player.level();
    if (portalix$level != level || !portalix$delivery.delivered(chunk)) return false;
    if (!portalix$delivery.retiring(chunk)) return true;

    var loaded = level.getChunkSource().getChunkNow(ChunkPos.getX(chunk), ChunkPos.getZ(chunk));
    if (loaded == null) return false;
    ChunkMapAccessor.portalix$markChunkPendingToSend(player, loaded);
    return true;
  }

  @Override
  public boolean portalix$received(ServerLevel level, long chunk) {
    return portalix$delivered(level, chunk) && !portalix$refreshes.containsKey(chunk);
  }

  @Override
  public void portalix$invalidate(ServerPlayer player, long chunk) {
    if (portalix$level != player.level() || !portalix$delivery.presentColumns().contains(chunk)) return;
    portalix$delivery.invalidate(chunk);
    portalix$discardRefresh(chunk);
    portalix$refreshes.put(chunk, 0);
    PlayerPortalVisibility.of(player).suspendColumn(chunk, status -> PortalChunkTracker.sendViewStatus(player, status));
  }

  @Override
  public void portalix$confirmRefresh(ServerPlayer player, int id) {
    if (player.level() != portalix$level) return;
    var batch = portalix$refreshBatches.remove(id);
    if (batch == null) return;
    for (long key : batch) portalix$refreshes.remove(key);
    PortalServerEvents.chunkRefreshConfirmed(player);
  }

  @Unique
  private void portalix$discardRefresh(long chunk) {
    int id = portalix$refreshes.remove(chunk);
    if (id == 0) return;
    var batch = portalix$refreshBatches.get(id);
    batch.remove(chunk);
    if (batch.isEmpty()) portalix$refreshBatches.remove(id);
  }

  @WrapMethod(method = "sendNextChunks")
  private void portalix$observers(ServerPlayer player, Operation<Void> original) {
    portalix$enterLevel(player);
    if (portalix$delivery.hasRetirements()) {
      if (!player.isAlive()) return;
      portalix$retirementQuota = Math.min(
          portalix$retirementQuota + desiredChunksPerTick, Math.max(1, desiredChunksPerTick));
    } else {
      portalix$retirementQuota = 0;
    }
    portalix$selected = false;
    if (memoryConnection && !pendingChunks.isEmpty())
      LocalChunkPalettes.send(() -> original.call(player));
    else original.call(player);
    // The client only acknowledges full-chunk batches. Retirement must also progress between them.
    int limit = Mth.floor(portalix$retirementQuota);
    if (!portalix$selected && limit > 0 && portalix$delivery.hasRetirements())
      portalix$forget(player, portalix$delivery.drainRetirements(limit));
  }

  @WrapOperation(method = "sendNextChunks", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/server/network/PlayerChunkSender;collectChunksToSend(Lnet/minecraft/server/level/ChunkMap;Lnet/minecraft/world/level/ChunkPos;)Ljava/util/List;"),
      require = 1, allow = 1)
  private List<LevelChunk> portalix$prioritizeBatch(PlayerChunkSender sender, ChunkMap map,
      ChunkPos center, Operation<List<LevelChunk>> original, @Local(argsOnly = true) ServerPlayer player) {
    portalix$selected = true;
    var observers = PortalChunkObservers.of(player);
    int quota = Mth.floor(batchQuota);
    var selected =
        ChunkSendQueue.nearest(
            pendingChunks, quota, observers::priority, key -> map.getChunkToSend(key) != null);
    int limit = Mth.floor(portalix$retirementQuota);
    if (limit > 0 && portalix$delivery.hasRetirements()) {
      var batch = portalix$delivery.select(selected, quota, limit, observers::distanceSquared);
      selected = batch.loaded();
      portalix$forget(player, batch.forgotten());
    }
    var chunks = new ArrayList<LevelChunk>(selected.length);
    for (long key : selected) {
      var chunk = map.getChunkToSend(key);
      if (chunk != null) {
        pendingChunks.remove(key);
        chunks.add(chunk);
      }
    }
    return chunks;
  }

  @WrapOperation(
      method = "sendNextChunks",
      slice = @Slice(from = @At(value = "NEW",
          target = "net/minecraft/network/protocol/game/ClientboundChunkBatchFinishedPacket")),
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"),
      require = 1, allow = 1)
  private void portalix$trackBatch(
      ServerGamePacketListenerImpl connection, Packet<?> packet, Operation<Void> original,
      @Local List<LevelChunk> loaded) {
    original.call(connection, packet);
    portalix$fenceRefreshes(connection, loaded);
    PortalEntityTracking.refreshDeliveredColumns(connection.player, loaded);
  }

  @Unique
  private void portalix$fenceRefreshes(ServerGamePacketListenerImpl connection, List<LevelChunk> loaded) {
    if (portalix$refreshes.isEmpty()) return;
    LongOpenHashSet refreshed = null;
    for (var chunk : loaded) {
      long key = chunk.getPos().pack();
      if (portalix$refreshes.getOrDefault(key, -1) != 0 || !portalix$delivery.delivered(key)) continue;
      if (refreshed == null) refreshed = new LongOpenHashSet();
      refreshed.add(key);
    }
    if (refreshed == null) return;
    int id;
    do {
      id = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
    } while (portalix$refreshBatches.containsKey(id));
    for (long key : refreshed) portalix$refreshes.put(key, id);
    portalix$refreshBatches.put(id, refreshed);
    // The client handles Ping on the same thread as chunk installation. The native batch ACK
    // runs on the network thread and can precede installation of the full snapshot.
    connection.send(new ClientboundPingPacket(id));
  }

  @Unique
  private void portalix$forget(ServerPlayer player, long[] forgotten) {
    portalix$retirementQuota -= forgotten.length;
    PortalEntityTracking.refreshForgottenColumns(player, forgotten);
    var visibility = PlayerPortalVisibility.of(player);
    for (long chunk : forgotten) {
      portalix$discardRefresh(chunk);
      visibility.forgetColumn(chunk);
      player.connection.send(new ClientboundForgetLevelChunkPacket(ChunkPos.unpack(chunk)));
      PortalServerEvents.chunkDeliveryChanged(player, chunk);
    }
  }
}
