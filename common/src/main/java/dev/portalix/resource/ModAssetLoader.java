package dev.portalix.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class ModAssetLoader {
  private ModAssetLoader() {}

  public static InputStream open(String path) {
    return Objects.requireNonNull(
        ModAssetLoader.class.getResourceAsStream(path), "Missing bundled Portalix asset " + path);
  }

  public static JsonObject json(String path) {
    try (var reader = new InputStreamReader(open(path), StandardCharsets.UTF_8)) {
      return JsonParser.parseReader(reader).getAsJsonObject();
    } catch (IOException | JsonParseException | IllegalStateException failure) {
      throw new IllegalStateException("Cannot load bundled Portalix JSON " + path, failure);
    }
  }
}
