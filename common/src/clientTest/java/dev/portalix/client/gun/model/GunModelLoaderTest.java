package dev.portalix.client.gun.model;

import static org.junit.jupiter.api.Assertions.*;

import dev.portalix.resource.ModAssetLoader;
import java.io.IOException;
import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GunModelLoaderTest {
  @ParameterizedTest
  @ValueSource(strings = {"view", "world"})
  void bundledMeshLoadsWithItsMetadata(String kind) throws IOException {
    var asset = GunModelLoader.load(kind);
    assertFalse(asset.groups().isEmpty());
    for (var group : asset.groups()) {
      String path = "/assets/portalix/textures/model/" + group.material().toLowerCase(Locale.ROOT) + ".png";
      try (var texture = ModAssetLoader.open(path)) {
        assertTrue(texture.read() >= 0, "Empty model texture: " + path);
      }
    }
    assertTrue(asset.clips().containsKey("idle"));
    assertEquals(GunAttachment.values().length, asset.attachments().size());
    assertEquals(GunPose.values().length, asset.presentation().size());
    assertEquals(kind.equals("world"), asset.world());
  }
}
