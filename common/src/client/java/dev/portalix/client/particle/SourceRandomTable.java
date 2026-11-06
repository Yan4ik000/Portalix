package dev.portalix.client.particle;

import dev.portalix.resource.ModAssetLoader;
import java.io.DataInputStream;
import java.io.IOException;

final class SourceRandomTable {
  private static final float[] VALUES = load();

  private SourceRandomTable() {}

  static float value(int index) {
    return VALUES[index & 4095];
  }

  private static float[] load() {
    try (var input =
        new DataInputStream(
            ModAssetLoader.open("/assets/portalix/source_particles/projectile_random.bin"))) {
      float[] values = new float[4096];
      for (int i = 0; i < values.length; i++) values[i] = input.readFloat();
      return values;
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot load Source particle random table", exception);
    }
  }
}
