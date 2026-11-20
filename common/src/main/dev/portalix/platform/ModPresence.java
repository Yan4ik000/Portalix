package dev.portalix.platform;

import java.util.ServiceLoader;

public interface ModPresence {
  ModPresence INSTANCE =
      ServiceLoader.load(ModPresence.class, ModPresence.class.getClassLoader())
          .findFirst()
          .orElseThrow();

  boolean isLoaded(String id);
}
