package dev.portalix.sound;

import dev.portalix.resource.SourceUnits;

public final class SourceSoundAttenuation {
  private SourceSoundAttenuation() {}

  public static float gain(int soundLevel, double blocks) {
    if (soundLevel == 0) return 1;
    float distance = (float) (blocks / SourceUnits.SOURCE_UNIT_BLOCKS);

    if (distance >= 32768) return .001F;
    float multiplier = (float) ((1000.0 / power10(soundLevel * .05F)) / 36.0);
    float scaled =
        (float)
            ((double) distance * multiplier * power10(distance * .0008333333535119891F * 4 * .05F));
    float gain = scaled > .1 ? 1 / scaled : 10;
    if (gain > .5F) {
      int level = (int) (float) (20 * Math.log10(1000.0 / ((double) multiplier * 36)));
      float exponent = 2.5F;
      if (level > 90) exponent -= (level - 90) * 1.7F * .02F;
      float knee = (float) (-1.0 / (power(.5F, exponent) * -.5));
      gain = (float) (1 - 1.0 / ((double) power(gain, exponent) * knee));
    }
    if (gain < .01F) {
      gain = (2 - .01F * scaled) * .01F;
      if (gain <= 0) gain = .001F;
    }
    return gain;
  }

  public static double occlusionRadius(int soundLevel) {
    return Math.max(0, (soundLevel - 60) * 2.7 + 24) * SourceUnits.SOURCE_UNIT_BLOCKS;
  }

  public static double occlusionGain(int obscuredSamples) {
    return Math.pow(10, -2.7 * Math.max(0, obscuredSamples - 1) / 20);
  }

  private static float power10(float value) {
    return power2(value * 3.321928024291992F);
  }

  private static float power(float value, float exponent) {
    if (value <= 1.1920928955078125e-7F) return 0;
    float logarithm = (float) Float.floatToRawIntBits(value) * 1.1920928955078125e-7F - 127;
    float fraction = logarithm - (float) Math.floor(logarithm);
    return power2((logarithm + (fraction - fraction * fraction) * .346607F) * exponent);
  }

  private static float power2(float exponent) {
    float fraction = exponent - (float) Math.floor(exponent);
    return Float.intBitsToFloat(
        (int) ((exponent + 127 - (fraction - fraction * fraction) * .33971F) * 8388608));
  }
}
