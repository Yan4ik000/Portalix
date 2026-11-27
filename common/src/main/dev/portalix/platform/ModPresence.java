package dev.portalix.platform;

import java.io.IOException;
import java.io.InputStream;
import java.util.ServiceLoader;
import org.jspecify.annotations.Nullable;

public interface ModPresence {
  ModPresence INSTANCE =
      ServiceLoader.load(ModPresence.class, ModPresence.class.getClassLoader())
          .findFirst()
          .orElseThrow();

  boolean isLoaded(String id);

  @Nullable InputStream openResource(String id, String path) throws IOException;
}
