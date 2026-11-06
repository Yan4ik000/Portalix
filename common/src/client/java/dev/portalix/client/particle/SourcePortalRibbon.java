package dev.portalix.client.particle;

import org.joml.Vector3f;

final class SourcePortalRibbon {
  record Control(Vector3f position, float radius, Vector3f normal, Vector3f color, float alpha) {}

  record Vertex(Vector3f position, float u, float v, Vector3f color, float alpha) {}

  private SourcePortalRibbon() {}

  static Vertex evaluate(
      Control a, Control b, Control c, Control d, float t, float side, float scroll) {
    Vector3f position = new Vector3f(), tangent = new Vector3f();
    for (int axis = 0; axis < 3; axis++) {
      position.setComponent(
          axis,
          ParticleSpline.position(
              a.position.get(axis),
              b.position.get(axis),
              c.position.get(axis),
              d.position.get(axis),
              t));
      tangent.setComponent(
          axis,
          ParticleSpline.tangent(
              a.position.get(axis),
              b.position.get(axis),
              c.position.get(axis),
              d.position.get(axis),
              t));
    }
    Vector3f normal = new Vector3f(b.normal).lerp(c.normal, t);
    normal.cross(tangent);
    if (normal.lengthSquared() > 0) normal.normalize();
    float radius = ParticleSpline.position(a.radius, b.radius, c.radius, d.radius, t);
    position.add(normal.mul(radius * (side - .5F)));
    Vector3f color = linear(b.color).lerp(linear(c.color), t);
    float alpha = packed(b.alpha) * (1 - t) + packed(c.alpha) * t;
    return new Vertex(position, 1 - side, scroll, color, alpha);
  }

  private static float packed(float value) {
    return SourceParticleColor.packedColor(Math.clamp(value, 0, 1));
  }

  private static Vector3f linear(Vector3f color) {
    return new Vector3f(
        SourceParticleColor.linearColor(color.x),
        SourceParticleColor.linearColor(color.y),
        SourceParticleColor.linearColor(color.z));
  }
}
