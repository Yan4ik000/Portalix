package dev.portalix.client.compat;

import dev.portalix.platform.ModPresence;
import java.util.ServiceLoader;

final class OptionalRenderServices {
  private OptionalRenderServices() {}

  static <T> T load(Class<T> service, String mod) {
    if (!ModPresence.INSTANCE.isLoaded(mod)) return null;
    var providers = ServiceLoader.load(service).iterator();
    if (!providers.hasNext())
      throw new IllegalStateException("Missing Portalix render integration for " + mod);
    T provider = providers.next();
    if (providers.hasNext()) throw new IllegalStateException("Multiple render integrations for " + mod);
    return provider;
  }
}
