package dev.portalix.neoforge;

import dev.portalix.platform.ModPresence;
import java.io.IOException;
import java.io.InputStream;
import net.neoforged.fml.loading.FMLLoader;
import org.jspecify.annotations.Nullable;

public final class NeoForgeModPresence implements ModPresence {
  @Override
  public boolean isLoaded(String id) {
    return FMLLoader.getCurrent().getLoadingModList().getModFileById(id) != null;
  }

  @Override
  public @Nullable InputStream openResource(String id, String path) throws IOException {
    var modFile = FMLLoader.getCurrent().getLoadingModList().getModFileById(id);
    return modFile == null ? null : modFile.getFile().getContents().openFile(path);
  }
}
