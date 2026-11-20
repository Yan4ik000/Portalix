package dev.portalix.mixin.world.tracking;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
  @Accessor("level")
  ServerLevel portalix$level();

  @Accessor("serverViewDistance")
  int portalix$serverViewDistance();

  @Invoker("getPlayerViewDistance")
  int portalix$playerViewDistance(ServerPlayer player);

  @Accessor("entityMap")
  Int2ObjectMap<?> portalix$entityTrackers();

  @Invoker("getVisibleChunkIfPresent")
  @Nullable ChunkHolder portalix$getVisibleChunk(long position);

  @Invoker("markChunkPendingToSend")
  static void portalix$markChunkPendingToSend(ServerPlayer player, LevelChunk chunk) {
    throw new AssertionError();
  }

  @Invoker("dropChunk")
  static void portalix$dropChunk(ServerPlayer player, ChunkPos pos) {
    throw new AssertionError();
  }
}
