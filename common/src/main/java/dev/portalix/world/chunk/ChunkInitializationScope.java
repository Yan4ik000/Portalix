package dev.portalix.world.chunk;

import net.minecraft.world.level.LevelReader;
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

  public static @Nullable LevelChunk find(LevelReader level, int x, int z) {
    var current = INITIALIZING.isBound() ? INITIALIZING.get() : null;
    for (var scope = current; scope != null; scope = scope.previous) {
      var chunk = scope.chunk;
      var pos = chunk.getPos();
      if (chunk.getLevel() == level && pos.x() == x && pos.z() == z) return chunk;
    }
    return null;
  }
}
