package dev.portalix.client.particle;

import dev.portalix.item.PortalixVariant;
import org.joml.Vector3f;

final class SourceParticleColor {
  private static final double GAMMA = 2.200000047683716;

  private SourceParticleColor() {}

  static Vector3f gunPalette(PortalixVariant variant, boolean secondary) {
    return channels(variant.particleColor(secondary));
  }

  private static Vector3f channels(int rgb) {
    return new Vector3f(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255);
  }

  static Vector3f portalPalette(PortalixVariant variant, boolean secondary) {
    if (!variant.usesTintedRim()) return gunPalette(variant, secondary);
    // Portal rim tints are linear; Source particle colors are gamma-encoded.
    var color = channels(variant.rimColor(secondary));
    for (int channel = 0; channel < 3; channel++)
      color.setComponent(channel, (float) Math.pow(color.get(channel) / 255.0, 1.0 / GAMMA) * 255);
    return color;
  }

  static float linearColor(float channel) {
    float packed = packedColor(channel);
    return (float) Math.pow(packed, GAMMA);
  }

  static float packedColor(float channel) {

    return (Float.floatToRawIntBits(channel * 255.0F + 8388608.0F) & 255) / 255.0F;
  }
}
