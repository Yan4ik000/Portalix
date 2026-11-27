package dev.portalix.fabric;

import dev.portalix.platform.ModPresence;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

public final class FabricModPresence implements ModPresence {
  @Override
  public boolean isLoaded(String id) {
    return FabricLoader.getInstance().isModLoaded(id);
  }

  @Override
  public @Nullable InputStream openResource(String id, String path) throws IOException {
    var resource = FabricLoader.getInstance().getModContainer(id).flatMap(mod -> mod.findPath(path));
    return resource.isPresent() ? Files.newInputStream(resource.get()) : null;
  }
}
