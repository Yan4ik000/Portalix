package dev.portalix.client.particle;

final class ParticleSpline {
  private ParticleSpline() {}

  static float position(float a, float b, float c, float d, float t) {
    float cubic = 3.0F * b - a - 3.0F * c + d;
    float quadratic = 2.0F * a - 5.0F * b + 4.0F * c - d;
    float linear = c - a;
    return b + 0.5F * t * (linear + t * (quadratic + t * cubic));
  }

  static float tangent(float a, float b, float c, float d, float t) {
    float cubic = 3.0F * b - a - 3.0F * c + d;
    float quadratic = 2.0F * a - 5.0F * b + 4.0F * c - d;
    return 0.5F * (c - a + t * (2.0F * quadratic + 3.0F * t * cubic));
  }

}
