package dev.portalix.client.particle;

import dev.portalix.resource.SourceUnits;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import net.minecraft.world.phys.Vec3;

final class ImpactSparkSimulation {
  private static final int MAX_PARTICLES = 300;
  final List<Spark> sparks = new ArrayList<>();
  final boolean validSurface;
  final double emissionStartTime;

  private static final double SOURCE_UNIT_BLOCKS = SourceUnits.SOURCE_UNIT_BLOCKS;
  private static final double MAX_SPEED = 350.0;
  private static final double GRAVITY = 500.0;
  private static final double STEP = 0.008333333333333333;

  double simulated;
  private int emitted;
  private int substep;

  ImpactSparkSimulation(
      Vec3 origin,
      Vec3 impact,
      Vec3 normal,
      boolean orange,
      int particleCount,
      boolean validSurface) {
    this.validSurface = validSurface;
    normal = normal.lengthSqr() >= 0.01 ? normal.normalize() : new Vec3(0.0, 1.0, 0.0);
    this.emissionStartTime = validSurface ? 0.05 : 0.0;

    long seed = Objects.hash(origin, impact, orange, validSurface);
    Random random = new Random(seed);

    Vec3 tangent =
        normal
            .cross(
                Math.abs(normal.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0))
            .normalize();
    Vec3 across = normal.cross(tangent).normalize();

    int count = Math.clamp(particleCount, 0, MAX_PARTICLES);
    for (int i = 0; i < count; i++) {
      double angle = random.nextDouble() * Math.PI * 2.0;
      double spread = random.nextDouble();
      Vec3 radial =
          tangent.scale(Math.cos(angle) * spread).add(across.scale(Math.sin(angle) * spread));

      double dist = (0.5 + random.nextDouble() * 2.5) * SOURCE_UNIT_BLOCKS;
      Vec3 offset = radial.scale(dist).add(normal.scale(0.02 * SOURCE_UNIT_BLOCKS));
      Vec3 start = impact.add(offset);

      double radialSpeed = (80.0 + random.nextDouble() * 120.0) * SOURCE_UNIT_BLOCKS;
      double normalSpeed =
          validSurface
              ? (70.0 + random.nextDouble() * 150.0) * SOURCE_UNIT_BLOCKS
              : (140.0 + random.nextDouble() * 260.0) * SOURCE_UNIT_BLOCKS;

      Vec3 velocity = radial.normalize().scale(radialSpeed).add(normal.scale(normalSpeed));
      if (velocity.length() > MAX_SPEED * SOURCE_UNIT_BLOCKS) {
        velocity = velocity.normalize().scale(MAX_SPEED * SOURCE_UNIT_BLOCKS);
      }

      double life =
          validSurface
              ? (0.35 + 0.45 * random.nextDouble())
              : (0.2 + 0.6 * Math.pow(random.nextDouble(), 3.0));
      double radius = (1.0 + random.nextDouble() * 0.8) * SOURCE_UNIT_BLOCKS;
      double trail = 0.2;
      this.sparks.add(new Spark(start, velocity, life, radius, trail));
    }
  }

  double radiusScale(double normalizedAge) {
    if (!Double.isFinite(normalizedAge) || normalizedAge < 0.0 || normalizedAge >= 1.0) {
      return 0.0;
    }
    return Math.clamp(1.0 - normalizedAge, 0.0, 1.0);
  }

  void advance(double age, CollisionTracer trace, int collisionParticles, int stride) {
    for (Spark spark : sparks) {
      spark.px = spark.x;
      spark.py = spark.y;
      spark.pz = spark.z;
    }
    double target = Math.min(age, simulated + 1.0);
    if (age - simulated > 1.0) {
      simulated = age;
      for (Spark spark : sparks) spark.birth = -2.0;
      emitted = sparks.size();
      return;
    }
    float step = (float) STEP;
    float damping = this.validSurface ? 0.08F : 0.05F;
    float momentum = SourceParticleKinematics.momentumScale(damping, step, step);
    double maxSpeed = MAX_SPEED * SOURCE_UNIT_BLOCKS;
    int emitPerStep = Math.max(1, (int) Math.ceil(sparks.size() * 0.4));

    while (simulated + STEP <= target + 1.0E-8) {
      if (simulated >= this.emissionStartTime) {
        for (int i = 0; i < emitPerStep && emitted < sparks.size(); i++) {
          sparks.get(emitted++).birth = simulated;
        }
      }
      boolean sampleCollision = (substep++ % 4 == 0);
      for (int index = 0; index < emitted; index += stride) {
        Spark spark = sparks.get(index);
        double particleAge = simulated - spark.birth;
        if (!Double.isFinite(spark.birth) || particleAge < 0.0 || particleAge >= spark.life)
          continue;

        double ax = 0.0;
        double ay = -GRAVITY * SOURCE_UNIT_BLOCKS;
        double az = 0.0;

        spark.vx = SourceParticleKinematics.velocity((float) spark.vx, (float) ax, step, momentum);
        spark.vy = SourceParticleKinematics.velocity((float) spark.vy, (float) ay, step, momentum);
        spark.vz = SourceParticleKinematics.velocity((float) spark.vz, (float) az, step, momentum);

        double speedSqr = spark.vx * spark.vx + spark.vy * spark.vy + spark.vz * spark.vz;
        if (speedSqr > maxSpeed * maxSpeed) {
          double scale = maxSpeed / Math.sqrt(speedSqr);
          spark.vx *= scale;
          spark.vy *= scale;
          spark.vz *= scale;
        }

        double x = spark.x + spark.vx * STEP;
        double y = spark.y + spark.vy * STEP;
        double z = spark.z + spark.vz * STEP;

        if (index < collisionParticles && sampleCollision) {
          Contact hit = trace.trace(new Vec3(spark.cx, spark.cy, spark.cz), new Vec3(x, y, z));
          if (hit != null && hit.normal().lengthSqr() >= 1.0E-12) {
            Vec3 n = hit.normal();
            x = hit.position().x + n.x * 0.002;
            y = hit.position().y + n.y * 0.002;
            z = hit.position().z + n.z * 0.002;
            double dot = spark.vx * n.x + spark.vy * n.y + spark.vz * n.z;
            spark.vx = (spark.vx - 2.0 * dot * n.x) * 0.5;
            spark.vy = (spark.vy - 2.0 * dot * n.y) * 0.5;
            spark.vz = (spark.vz - 2.0 * dot * n.z) * 0.5;
          }
          spark.cx = x;
          spark.cy = y;
          spark.cz = z;
        }

        spark.x = x;
        spark.y = y;
        spark.z = z;
      }
      simulated += STEP;
    }
  }

  @FunctionalInterface
  interface CollisionTracer {
    Contact trace(Vec3 start, Vec3 end);
  }

  record Contact(Vec3 position, Vec3 normal) {}

  static final class Spark {
    double x, y, z;
    double px, py, pz;
    double cx, cy, cz;
    double vx, vy, vz;
    final double life;
    final double radius;
    final double initialTrail;
    double birth = Double.NaN;

    Spark(Vec3 position, Vec3 velocity, double life, double radius, double trail) {
      x = px = cx = position.x;
      y = py = cy = position.y;
      z = pz = cz = position.z;
      vx = velocity.x;
      vy = velocity.y;
      vz = velocity.z;
      this.life = life;
      this.radius = radius;
      this.initialTrail = trail;
    }
  }
}
