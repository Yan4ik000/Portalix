package dev.portalix.client.mixin.world;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.Deque;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
abstract class PortalLightUpdatesMixin {
  @Shadow @Final private LevelExtractor levelExtractor;
  @Unique private final Long2LongOpenHashMap portalix$lightColumns = new Long2LongOpenHashMap();
  @Unique private boolean portalix$batchingLight;

  @WrapMethod(method = "pollLightUpdates")
  private void portalix$lightBatch(Operation<Void> original) {
    portalix$batchingLight = true;
    try {
      original.call();
    } finally {
      portalix$batchingLight = false;
      var columns = portalix$lightColumns.long2LongEntrySet().fastIterator();
      while (columns.hasNext()) {
        var entry = columns.next();
        int x = ChunkPos.getX(entry.getLongKey()), z = ChunkPos.getZ(entry.getLongKey());
        long range = entry.getLongValue();
        levelExtractor.setSectionRangeDirty(x, (int) range, z, x, (int) (range >> 32), z);
      }
      portalix$lightColumns.clear();
    }
  }

  @Inject(method = "setSectionRangeDirty", at = @At("HEAD"), cancellable = true)
  private void portalix$mergeLightColumns(
      int minX, int minY, int minZ, int maxX, int maxY, int maxZ, CallbackInfo ci) {
    if (!portalix$batchingLight) return;
    for (int x = minX; x <= maxX; x++) {
      for (int z = minZ; z <= maxZ; z++) {
        long key = ChunkPos.pack(x, z);
        long range = portalix$lightColumns.getOrDefault(key, ChunkPos.pack(minY, maxY));
        portalix$lightColumns.put(
            key, ChunkPos.pack(Math.min(minY, (int) range), Math.max(maxY, (int) (range >> 32))));
      }
    }
    ci.cancel();
  }

  @ModifyExpressionValue(
      method = "pollLightUpdates",
      at = @At(value = "INVOKE", target = "Ljava/util/Deque;size()I"),
      allow = 1)
  private int portalix$lightBudget(int queued, @Share("remaining") LocalIntRef remaining) {
    remaining.set(Math.max(10, queued / 10));
    return queued;
  }

  @WrapOperation(
      method = "pollLightUpdates",
      at = @At(value = "INVOKE", target = "Ljava/util/Deque;poll()Ljava/lang/Object;"),
      allow = 1)
  private Object portalix$continuousLighting(
      Deque<Runnable> queue,
      Operation<Object> original,
      @Share("remaining") LocalIntRef remaining) {
    if (remaining.get() == 0) return null;
    remaining.set(remaining.get() - 1);
    return original.call(queue);
  }
}
