package dev.portalix.neoforge.mixin;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerChunkCache.class)
abstract class PortalChunkLoadingLookupMixin {
  @Shadow @Final public ChunkMap chunkMap;

  @Definition(id = "cache", field = "Lnet/minecraft/server/level/ServerChunkCache;lastChunk:[Lnet/minecraft/world/level/chunk/ChunkAccess;")
  @Expression("this.cache[?]")
  @ModifyExpressionValue(method = {"getChunk", "getChunkNow"}, at = @At("MIXINEXTRAS:EXPRESSION"),
      require = 2, allow = 2)
  private ChunkAccess portalix$constructedAfterCachedMiss(ChunkAccess cached, int x, int z) {
    if (cached != null) return cached;
    // A cached miss would otherwise return before NeoForge's currentlyLoading lookup.
    var holder = chunkMap.getVisibleChunkIfPresent(ChunkPos.pack(x, z));
    return holder == null ? null : holder.currentlyLoading;
  }
}
