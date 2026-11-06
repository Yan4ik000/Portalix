package dev.portalix.client.particle;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.portalix.PortalixMod;
import dev.portalix.client.render.LinearParticleRenderer;
import dev.portalix.client.render.ParticleGeometry;
import dev.portalix.client.render.ParticleRenderTypes;
import dev.portalix.client.render.ParticleView;
import dev.portalix.network.gun.PortalImpactPayload;
import dev.portalix.resource.SourceUnits;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

final class ImpactSparkRenderer {
  private static final PoseStack IDENTITY_POSE = new PoseStack();
  private static final Identifier PLACED_IMPACT_TEXTURE = PortalixMod.id("textures/effects/spark.png");
  private static final Identifier FAILED_IMPACT_TEXTURE =
      PortalixMod.id("textures/effects/impact_sparks_sequence6.png");

  private static final double MIN_TRAIL = 0.1 * SourceUnits.SOURCE_UNIT_BLOCKS;
  private static final double MAX_PLACED_IMPACT_TRAIL = 7.0 * SourceUnits.SOURCE_UNIT_BLOCKS;
  private static final double MAX_FAILED_IMPACT_TRAIL = 5.0 * SourceUnits.SOURCE_UNIT_BLOCKS;

  private final ImpactSparkSimulation simulation;
  private final boolean portalPlaced;
  private final int color;

  ImpactSparkRenderer(PortalImpactPayload impact, int particleCount) {
    this.portalPlaced = impact.placed();
    this.color = impact.variant().particleGlowColor(impact.orange());
    this.simulation =
        new ImpactSparkSimulation(
            impact.origin(),
            impact.position(),
            impact.normal(),
            impact.orange(),
            particleCount,
            impact.placed());
  }

  void advance(
      double age, ImpactSparkSimulation.CollisionTracer trace, int collisionParticles, int stride) {
    this.simulation.advance(age, trace, collisionParticles, stride);
  }

  AABB bounds(double age, int stride) {
    double minX = Double.POSITIVE_INFINITY;
    double minY = minX;
    double minZ = minX;
    double maxX = Double.NEGATIVE_INFINITY;
    double maxY = maxX;
    double maxZ = maxX;
    int index = 0;

    while (index < this.simulation.sparks.size()) {
      ImpactSparkSimulation.Spark spark = this.simulation.sparks.get(index);
      double particleAge = age - spark.birth;
      if (Double.isFinite(spark.birth) && particleAge >= 0.0 && particleAge < spark.life) {
        minX = Math.min(minX, Math.min(spark.px, spark.x));
        minY = Math.min(minY, Math.min(spark.py, spark.y));
        minZ = Math.min(minZ, Math.min(spark.pz, spark.z));
        maxX = Math.max(maxX, Math.max(spark.px, spark.x));
        maxY = Math.max(maxY, Math.max(spark.py, spark.y));
        maxZ = Math.max(maxZ, Math.max(spark.pz, spark.z));
      }

      index += stride;
    }

    return minX == Double.POSITIVE_INFINITY
        ? null
        : new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(0.25);
  }

  void submit(double age, ParticleView view, OrderedSubmitNodeCollector collector, int stride) {
    float interpolation = (float) Math.clamp((age - this.simulation.simulated) * 20.0, 0.0, 1.0);
    Identifier texture = this.portalPlaced ? PLACED_IMPACT_TEXTURE : FAILED_IMPACT_TEXTURE;

    int overbright = this.portalPlaced ? 1 : 3;
    double maxTrail = this.portalPlaced ? MAX_PLACED_IMPACT_TRAIL : MAX_FAILED_IMPACT_TRAIL;

    LinearParticleRenderer.submit(
        collector,
        IDENTITY_POSE,
        ParticleRenderTypes.linearTintedParticle(texture, overbright, false),
        texture,
        (pose, buffer) -> {
          Vector3f a = new Vector3f();
          Vector3f b = new Vector3f();
          Vector3f edge = new Vector3f();
          Vector3f towards = new Vector3f();
          int index = 0;

          while (index < this.simulation.sparks.size()) {
            ImpactSparkSimulation.Spark spark = this.simulation.sparks.get(index);
            double particleAge = age - spark.birth;
            if (Double.isFinite(spark.birth) && particleAge >= 0.0 && particleAge < spark.life) {
              Vec3 camera = view.position();
              double x = spark.px + (spark.x - spark.px) * interpolation - camera.x;
              double y = spark.py + (spark.y - spark.py) * interpolation - camera.y;
              double z = spark.pz + (spark.z - spark.pz) * interpolation - camera.z;
              double speed =
                  Math.sqrt(spark.vx * spark.vx + spark.vy * spark.vy + spark.vz * spark.vz);
              double trail =
                  Math.max(
                      MIN_TRAIL,
                      Math.min(
                          maxTrail, speed * Math.max(0.0, spark.initialTrail - 0.2 * particleAge)));
              double dx = speed < 1.0E-4 ? 0 : spark.vx / speed * trail;
              double dy = speed < 1.0E-4 ? 0 : spark.vy / speed * trail;
              double dz = speed < 1.0E-4 ? 0 : spark.vz / speed * trail;
              float radius =
                  (float) (spark.radius * this.simulation.radiusScale(particleAge / spark.life));

              int baseAlpha = this.portalPlaced ? 255 : 220;
              double fadeIn = Math.min(1.0, particleAge / 0.04);
              double fadeOut =
                  Math.clamp(
                      (spark.life - particleAge) / Math.max(0.01, spark.life * 0.4), 0.0, 1.0);
              int alpha = Math.clamp((int) (baseAlpha * fadeIn * fadeOut), 0, 255);
              a.set((float) (x - dx), (float) (y - dy), (float) (z - dz));
              b.set((float) x, (float) y, (float) z);
              edge.set(b).sub(a).cross(towards.set(a).negate());
              if (edge.lengthSquared() < 1.0E-10) {
                edge.set(1.0F, 0.0F, 0.0F);
              }
              edge.normalize();
              int colorR = color >> 16 & 255;
              int colorG = color >> 8 & 255;
              int colorB = color & 255;
              ParticleGeometry.strip(
                  pose, buffer, a, b, edge, edge, radius, radius, 0.0F, 1.0F, colorR, colorG,
                  colorB, 0, alpha);
            }

            index += stride;
          }
        });
  }
}
