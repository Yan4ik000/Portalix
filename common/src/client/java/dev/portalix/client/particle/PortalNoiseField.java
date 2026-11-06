package dev.portalix.client.particle;

import dev.portalix.resource.ModAssetLoader;
import java.io.DataInputStream;
import java.io.IOException;

final class PortalNoiseField {
  private static final int[][] PERMUTATION = new int[3][256];
  private static final float[] VALUES = new float[256];

  static {
    try (var input =
        new DataInputStream(ModAssetLoader.open("/assets/portalix/portals/noise.bin"))) {
      for (int[] table : PERMUTATION)
        for (int i = 0; i < table.length; i++) table[i] = input.readInt();
      for (int i = 0; i < VALUES.length; i++) VALUES[i] = input.readFloat();
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private PortalNoiseField() {}

  static float sample(float x, float y, float z) {
    int bx = Float.floatToRawIntBits(x + 32768.0F) & 65535;
    int by = Float.floatToRawIntBits(y + 32768.0F) & 65535;
    int bz = Float.floatToRawIntBits(z + 32768.0F) & 65535;
    int ix = bx >> 8, iy = by >> 8, iz = bz >> 8;
    float fx = (bx & 255) / 256.0F, fy = (by & 255) / 256.0F, fz = (bz & 255) / 256.0F;
    float low =
        lerp(
            lerp(value(ix, iy, iz), value(ix + 1, iy, iz), fx),
            lerp(value(ix, iy + 1, iz), value(ix + 1, iy + 1, iz), fx),
            fy);
    float high =
        lerp(
            lerp(value(ix, iy, iz + 1), value(ix + 1, iy, iz + 1), fx),
            lerp(value(ix, iy + 1, iz + 1), value(ix + 1, iy + 1, iz + 1), fx),
            fy);
    return (lerp(low, high, fz) - 0.5F) * 2;
  }

  private static float lerp(float a, float b, float t) {
    return (b - a) * t + a;
  }

  private static float value(int x, int y, int z) {
    return VALUES[PERMUTATION[2][(PERMUTATION[1][(PERMUTATION[0][x & 255] + y) & 255] + z) & 255]];
  }
}
