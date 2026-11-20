package dev.portalix.client.compat;

import java.util.ServiceLoader;

final class OptionalRenderServices {
  private OptionalRenderServices() {}

  static <T> T require(Class<T> service, String mod) {
    var providers = ServiceLoader.load(service).iterator();
    if (!providers.hasNext())
      throw new IllegalStateException("Missing Portalix render integration for " + mod);
    T provider = providers.next();
    if (providers.hasNext()) throw new IllegalStateException("Multiple render integrations for " + mod);
    return provider;
  }
}
