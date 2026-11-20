package dev.portalix.client.particle;

import org.joml.Vector3f;

final class SourcePortalRibbon {
  record Control(Vector3f position, float radius, Vector3f normal, Vector3f color, float alpha) {}

  record Sample(Vector3f left, Vector3f right, Vector3f color, float alpha) {}

  private SourcePortalRibbon() {}

  static Sample evaluate(Control a, Control b, Control c, Control d, float t) {
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
    var left = new Vector3f(position).add(new Vector3f(normal).mul(radius * -.5F));
    var right = position.add(normal.mul(radius * .5F));
    Vector3f color =
        SourceParticleColor.linearColor(b.color).lerp(SourceParticleColor.linearColor(c.color), t);
    float alpha =
        SourceParticleColor.packedAlpha(b.alpha) * (1 - t)
            + SourceParticleColor.packedAlpha(c.alpha) * t;
    return new Sample(left, right, color, alpha);
  }

}
