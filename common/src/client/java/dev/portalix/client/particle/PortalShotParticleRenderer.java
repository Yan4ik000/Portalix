package dev.portalix.client.particle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.portalix.PortalixMod;
import dev.portalix.client.render.LinearParticleRenderer;
import dev.portalix.client.render.ParticleGeometry;
import dev.portalix.client.render.ParticleRenderTypes;
import dev.portalix.client.render.ParticleView;
import java.util.List;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

final class PortalShotParticleRenderer {
  private static final Identifier FIBER = PortalixMod.id("textures/effects/projectile_fiber.png");
  private static final Identifier BALL = PortalixMod.id("textures/effects/particle_glow_01.png");
  private static final PoseStack POSE = new PoseStack();

  private PortalShotParticleRenderer() {}

  static void submit(
      PortalShotParticleSystem simulation, ParticleView view, OrderedSubmitNodeCollector collector) {
    Vec3 camera = view.position();
    for (PortalShotParticleSystem.Collection collection : simulation.collections) {
      if (collection.particles.isEmpty()) continue;
      Identifier texture = collection.program.rope ? FIBER : BALL;
      LinearParticleRenderer.submit(
          collector,
          POSE,
          ParticleRenderTypes.linearSpritecard(texture, 1, false),
          texture,
          (pose, builder) -> {
            if (collection.program.rope) rope(pose, builder, collection, camera);
            else {
              for (PortalShotParticleSystem.Particle particle : collection.particles) {
                Vector3f center = particle.rendered(camera);
                Vector3f right = view.right().toVector3f();
                Vector3f up = view.up().toVector3f();
                ParticleGeometry.sprite(
                    pose,
                    builder,
                    center,
                    right,
                    up,
                    particle.radius,
                    color(SourceParticleColor.packedColor(particle.rgb.x)),
                    color(SourceParticleColor.packedColor(particle.rgb.y)),
                    color(SourceParticleColor.packedColor(particle.rgb.z)),
                    color(
                        SourceParticleColor.packedColor(
                            Math.min(1.0F, particle.alpha * particle.alpha2))));
              }
            }
          });
    }
  }

  private static int color(float value) {
    return Math.clamp(Math.round(value * 255.0F), 0, 255);
  }

  private static int gammaColor(float linear) {
    return color((float) Math.pow(linear, 1.0 / 2.200000047683716));
  }

  private static void rope(
      PoseStack.Pose pose,
      VertexConsumer builder,
      PortalShotParticleSystem.Collection collection,
      Vec3 camera) {
    List<PortalShotParticleSystem.Particle> particles = collection.particles;
    if (particles.size() < 2) return;
    int count = (particles.size() - 1) * collection.program.subdivisions + 1;
    Vector3f[] points = new Vector3f[count];
    Vector3f[] edges = new Vector3f[count];
    float[] radii = new float[count];
    float[] alphas = new float[count];
    Vector3f[] colors = new Vector3f[count];
    Vector3f[] controls = new Vector3f[particles.size()];
    for (int i = 0; i < particles.size(); i++) controls[i] = particles.get(i).rendered(camera);
    int index = 0;
    for (int segment = 0; segment < particles.size() - 1; segment++) {
      PortalShotParticleSystem.Particle from = particles.get(segment);
      PortalShotParticleSystem.Particle to = particles.get(segment + 1);
      Vector3f a =
          segment > 0
              ? controls[segment - 1]
              : new Vector3f(controls[segment]).mul(2).sub(controls[segment + 1]);
      Vector3f b = controls[segment];
      Vector3f c = controls[segment + 1];
      Vector3f d =
          segment + 2 < controls.length
              ? controls[segment + 2]
              : new Vector3f(controls[segment + 1]).mul(2).sub(controls[segment]);
      for (int subdivision = 0; subdivision < collection.program.subdivisions; subdivision++, index++) {
        float t = (float) subdivision / collection.program.subdivisions;
        points[index] =
            new Vector3f(
                ParticleSpline.position(a.x, b.x, c.x, d.x, t),
                ParticleSpline.position(a.y, b.y, c.y, d.y, t),
                ParticleSpline.position(a.z, b.z, c.z, d.z, t));
        float radiusBefore =
            segment > 0 ? particles.get(segment - 1).radius : 2 * from.radius - to.radius;
        float radiusAfter =
            segment + 2 < particles.size()
                ? particles.get(segment + 2).radius
                : 2 * to.radius - from.radius;
        radii[index] =
            ParticleSpline.position(radiusBefore, from.radius, to.radius, radiusAfter, t) * 0.5F;
        edges[index] =
            new Vector3f(
                ParticleSpline.tangent(a.x, b.x, c.x, d.x, t),
                ParticleSpline.tangent(a.y, b.y, c.y, d.y, t),
                ParticleSpline.tangent(a.z, b.z, c.z, d.z, t));
        float fromAlpha = SourceParticleColor.packedColor(from.alpha * from.alpha2);
        float toAlpha = SourceParticleColor.packedColor(to.alpha * to.alpha2);
        alphas[index] = fromAlpha + (toAlpha - fromAlpha) * t;
        colors[index] =
            new Vector3f(
                    SourceParticleColor.linearColor(from.rgb.x),
                    SourceParticleColor.linearColor(from.rgb.y),
                    SourceParticleColor.linearColor(from.rgb.z))
                .lerp(
                    new Vector3f(
                        SourceParticleColor.linearColor(to.rgb.x),
                        SourceParticleColor.linearColor(to.rgb.y),
                        SourceParticleColor.linearColor(to.rgb.z)),
                    t);
      }
    }
    PortalShotParticleSystem.Particle last = particles.getLast();
    points[index] = controls[controls.length - 1];
    radii[index] = last.radius * 0.5F;
    edges[index] =
        new Vector3f(controls[index / collection.program.subdivisions])
            .sub(controls[index / collection.program.subdivisions - 1]);
    alphas[index] = SourceParticleColor.packedColor(last.alpha * last.alpha2);
    colors[index] =
        new Vector3f(
            SourceParticleColor.linearColor(last.rgb.x),
            SourceParticleColor.linearColor(last.rgb.y),
            SourceParticleColor.linearColor(last.rgb.z));
    for (int i = 0; i < count; i++) {
      Vector3f side = edges[i].cross(new Vector3f(points[i]).negate());
      edges[i] = side.lengthSquared() > 0.0F ? side.normalize() : side;
    }
    float uv = 0.0F;
    for (int i = 0; i < count - 1; i++) {
      int segment = i / collection.program.subdivisions;
      float next =
          uv
              + controls[segment].distance(controls[segment + 1])
                  * collection.program.texelScale
                  / collection.program.subdivisions;
      Vector3f from = colors[i];
      Vector3f to = colors[i + 1];
      ParticleGeometry.strip(
          pose,
          builder,
          points[i],
          points[i + 1],
          edges[i],
          edges[i + 1],
          radii[i],
          radii[i + 1],
          uv,
          next,
          gammaColor(from.x),
          gammaColor(from.y),
          gammaColor(from.z),
          gammaColor(to.x),
          gammaColor(to.y),
          gammaColor(to.z),
          color(alphas[i]),
          color(alphas[i + 1]));
      uv = next;
    }
  }
}
