package dev.portalix.client.particle;

import static org.junit.jupiter.api.Assertions.*;

import dev.portalix.resource.ModAssetLoader;
import java.io.IOException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ParticleRecordingAssetsTest {
  @ParameterizedTest
  @ValueSource(strings = {"holding_fp", "holding_tp"})
  void bundledRecordingUsesAvailableParticleSystemsAndTextures(String name) throws IOException {
    String path = "/assets/portalix/source_particles/" + name;
    var recording = new ParticleRecording(ModAssetLoader.open(path + ".pgp"));
    var materials = new ParticleMaterialLibrary();
    float loopStart = ModAssetLoader.json(path + ".playback.json").get("loopStart").getAsFloat();
    var player = new ParticleRecordingPlayer(recording, materials.ropes(), loopStart);
    assertFalse(player.systems().isEmpty());
    for (String system : player.systems()) {
      var texture = materials.system(system).material().texture();
      try (var resource = ModAssetLoader.open("/assets/" + texture.getNamespace() + "/" + texture.getPath())) {
        assertTrue(resource.read() >= 0, "Empty particle texture: " + texture);
      }
    }
  }
}
