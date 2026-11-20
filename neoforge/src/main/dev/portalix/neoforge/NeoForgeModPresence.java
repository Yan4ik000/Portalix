package dev.portalix.neoforge;

import dev.portalix.platform.ModPresence;
import net.neoforged.fml.loading.FMLLoader;

public final class NeoForgeModPresence implements ModPresence {
  @Override
  public boolean isLoaded(String id) {
    return FMLLoader.getCurrent().getLoadingModList().getModFileById(id) != null;
  }
}
