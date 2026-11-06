package dev.portalix.mixin.world.tracking;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
  @Accessor("entityMap")
  Int2ObjectMap<?> portalix$entityTrackers();

  @Invoker("markChunkPendingToSend")
  static void portalix$markChunkPendingToSend(ServerPlayer player, LevelChunk chunk) {
    throw new AssertionError();
  }

  @Invoker("dropChunk")
  static void portalix$dropChunk(ServerPlayer player, ChunkPos pos) {
    throw new AssertionError();
  }
}
