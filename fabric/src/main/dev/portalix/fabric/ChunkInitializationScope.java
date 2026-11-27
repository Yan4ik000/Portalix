package dev.portalix.fabric;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

public final class ChunkInitializationScope {
  private static final ScopedValue<Scope> INITIALIZING = ScopedValue.newInstance();

  private record Scope(LevelChunk chunk, @Nullable Scope previous) {}

  private ChunkInitializationScope() {}

  public static void run(LevelChunk chunk, Runnable action) {
    var previous = INITIALIZING.isBound() ? INITIALIZING.get() : null;
    ScopedValue.where(INITIALIZING, new Scope(chunk, previous)).run(action);
  }

  public static @Nullable LevelChunk find(ServerLevel level, int x, int z) {
    if (!INITIALIZING.isBound() || !level.getServer().isSameThread()) return null;
    for (var scope = INITIALIZING.get(); scope != null; scope = scope.previous) {
      var chunk = scope.chunk;
      var pos = chunk.getPos();
      if (chunk.getLevel() == level && pos.x() == x && pos.z() == z) return chunk;
    }
    return null;
  }
}
