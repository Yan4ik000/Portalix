package dev.portalix.test;

import java.util.Locale;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

public interface ChunkAttachmentTestHooks {
  enum Key {
    STALE, CURRENT, CLIENT;

    public Identifier id() {
      return Identifier.fromNamespaceAndPath("portalix_regression", name().toLowerCase(Locale.ROOT));
    }
  }

  static ChunkAttachmentTestHooks get() {
    return Registration.hooks;
  }

  static void register(ChunkAttachmentTestHooks hooks) {
    Registration.hooks = hooks;
  }

  @Nullable Integer get(LevelChunk chunk, Key key);

  void set(LevelChunk chunk, Key key, @Nullable Integer value);

  final class Registration {
    private static ChunkAttachmentTestHooks hooks;

    private Registration() {}
  }
}
