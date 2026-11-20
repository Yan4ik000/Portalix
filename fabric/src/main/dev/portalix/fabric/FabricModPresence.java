package dev.portalix.fabric;

import dev.portalix.platform.ModPresence;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricModPresence implements ModPresence {
  @Override
  public boolean isLoaded(String id) {
    return FabricLoader.getInstance().isModLoaded(id);
  }
}
