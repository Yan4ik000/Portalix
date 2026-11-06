package dev.portalix.client.particle;

final class SourceParticleKinematics {
  private SourceParticleKinematics() {}

  static float remap(float value, float low, float high, float outLow, float outHigh) {
    if (low == high) return value >= high ? outHigh : outLow;
    return outLow + (outHigh - outLow) * Math.clamp((value - low) / (high - low), 0.0F, 1.0F);
  }

  static float sourceSine(float phase) {
    float magnitude = Math.abs(phase);
    float wave = magnitude - 2.0F * (float) Math.floor(magnitude * 0.5F);
    boolean negative = wave >= 1.0F;
    if (negative) wave -= 1.0F;
    float result = (4.0F - 4.0F * wave) * wave;
    return negative ^ (phase < 0.0F) ? -result : result;
  }

  static float fade(float value, float low, float high, float start, float end) {
    float t = remap(value, low, high, 0.0F, 1.0F);
    float smooth = 3.0F * t * t - 2.0F * t * t * t;
    return start + (end - start) * smooth;
  }

  public static float momentumScale(float drag, float timestep, float previousTimestep) {
    float remaining = (float) (1.0 - Math.max(0.0F, drag));
    float exponent = (float) StrictMath.log(remaining);
    exponent *= 29.999998F;
    exponent *= timestep;
    float damping = (float) StrictMath.exp(exponent);
    return damping * (timestep / previousTimestep);
  }

  public static float movement(
      float position, float previous, float acceleration, float timestep, float momentum) {
    float displacement = (position - previous) * momentum;
    displacement += acceleration * (timestep * timestep);
    return displacement + position;
  }

  public static float velocity(float velocity, float acceleration, float timestep, float momentum) {
    return movement(0.0F, -velocity * timestep, acceleration, timestep, momentum) / timestep;
  }
}
