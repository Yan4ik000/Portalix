package dev.portalix.fabric.mixin;

import dev.portalix.fabric.ChunkInitializationScope;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerChunkCache.class)
abstract class PortalChunkLoadingLookupMixin {
  @Shadow @Final private ServerLevel level;

  @Inject(method = "getChunk", at = @At("HEAD"), cancellable = true)
  private void portalix$initializingChunk(int x, int z, ChunkStatus status, boolean load,
      CallbackInfoReturnable<ChunkAccess> cir) {
    var chunk = ChunkInitializationScope.find(level, x, z);
    if (chunk != null) cir.setReturnValue(chunk);
  }

  @Inject(method = "getChunkNow", at = @At("HEAD"), cancellable = true)
  private void portalix$initializingChunkNow(int x, int z, CallbackInfoReturnable<LevelChunk> cir) {
    var chunk = ChunkInitializationScope.find(level, x, z);
    if (chunk != null) cir.setReturnValue(chunk);
  }
}
