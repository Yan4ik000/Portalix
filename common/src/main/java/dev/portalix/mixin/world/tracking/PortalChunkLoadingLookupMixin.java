package dev.portalix.mixin.world.tracking;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.world.chunk.ChunkInitializationScope;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerChunkCache.class)
abstract class PortalChunkLoadingLookupMixin {
  @Shadow @Final private ServerLevel level;

  @WrapOperation(
      method = "getChunk",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/server/level/ServerChunkCache;getChunkFutureMainThread(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Ljava/util/concurrent/CompletableFuture;"))
  private CompletableFuture<ChunkResult<ChunkAccess>> portalix$constructedChunk(
      ServerChunkCache cache,
      int x,
      int z,
      ChunkStatus status,
      boolean load,
      Operation<CompletableFuture<ChunkResult<ChunkAccess>>> original) {
    var chunk = ChunkInitializationScope.find(level, x, z);
    return chunk == null
        ? original.call(cache, x, z, status, load)
        : CompletableFuture.completedFuture(ChunkResult.of(chunk));
  }

  @ModifyReturnValue(method = "getChunk", at = @At("RETURN"))
  private ChunkAccess portalix$cachedMiss(
      ChunkAccess result, int x, int z, ChunkStatus status, boolean load) {
    return result == null ? ChunkInitializationScope.find(level, x, z) : result;
  }

  @ModifyReturnValue(method = "getChunkNow", at = @At("RETURN"))
  private LevelChunk portalix$constructedChunkNow(LevelChunk result, int x, int z) {
    return result == null ? ChunkInitializationScope.find(level, x, z) : result;
  }

  @WrapWithCondition(
      method = "getChunk",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/server/level/ServerChunkCache;storeInCache(JLnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/chunk/status/ChunkStatus;)V"))
  private boolean portalix$keepConstructedChunkScoped(
      ServerChunkCache cache, long position, ChunkAccess chunk, ChunkStatus status) {
    return chunk == null
        || chunk
            != ChunkInitializationScope.find(
                level, ChunkPos.getX(position), ChunkPos.getZ(position));
  }
}
