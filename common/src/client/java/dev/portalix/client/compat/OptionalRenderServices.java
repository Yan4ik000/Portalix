package dev.portalix.client.compat;

import dev.portalix.platform.ModPresence;
import java.util.ServiceLoader;

public final class OptionalRenderServices {
  private OptionalRenderServices() {}

  public static <T> T load(Class<T> service, String mod) {
    if (!ModPresence.INSTANCE.isLoaded(mod)) return null;
    var providers = ServiceLoader.load(service).iterator();
    if (!providers.hasNext())
      throw new IllegalStateException("This Portalix build does not include " + mod + " integration");
    T provider = providers.next();
    if (providers.hasNext()) throw new IllegalStateException("Multiple render integrations for " + mod);
    return provider;
  }
}
