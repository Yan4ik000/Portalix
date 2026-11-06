package dev.portalix.client.particle;

import static dev.portalix.client.particle.SourceParticleKinematics.remap;

import dev.portalix.item.PortalixVariant;
import dev.portalix.resource.ModAssetLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

final class PortalShotParticleSystem {
  static final float LAUNCH_DELAY = 0.1F;
  static final float MIN_TRAVEL_TIME = 0.1F;
  private static final PortalShotParticleProgram.Library PROGRAMS =
      new PortalShotParticleProgram.Library(
          ModAssetLoader.json("/assets/portalix/source_particles/projectile.json")
              .getAsJsonObject("systems"));
  final List<Collection> collections = new ArrayList<>();
  final float stopTime;
  private final Vec3 start;
  private final Vec3 end;
  private final Vec3 auxiliary;
  private final Vector3f color;
  private final Vector3f tintScale;
  private float simulated;

  PortalShotParticleSystem(
      Vec3 start,
      Vec3 end,
      Vec3 launchDirection,
      boolean orange,
      float arrivalTime,
      PortalixVariant variant) {
    this.start = start;
    this.end = end;
    auxiliary = start.add(launchDirection.normalize().scale(start.distanceTo(end)));
    stopTime = Math.max(LAUNCH_DELAY + MIN_TRAVEL_TIME, arrivalTime);
    color = SourceParticleColor.gunPalette(variant, orange);
    tintScale =
        variant == PortalixVariant.ORANGE && orange
            ? new Vector3f(2.35F, 0.0F, 0.0F)
            : new Vector3f(1.0F);
    for (var child : PROGRAMS.children()) collections.add(new Collection(child));
  }

  Vec3 controlPoint(float time) {
    float progress = Math.clamp((time - LAUNCH_DELAY) / (stopTime - LAUNCH_DELAY), 0.0F, 1.0F);
    double remaining = 1.0 - progress;
    return start
        .scale(remaining)
        .add(auxiliary.scale(progress * remaining))
        .add(end.scale(progress * progress));
  }

  void advance(float time) {
    float remaining = time - simulated;
    if (remaining <= 0.0F) return;
    float maximumStep = PROGRAMS.maximumStep();
    remaining = Math.min(remaining, maximumStep * 10.0F);
    while (remaining > 0.0F) {
      float dt = Math.min(remaining, maximumStep);
      float previous = simulated;
      simulated += dt;
      remaining -= dt;
      for (Collection collection : collections) collection.advance(previous, simulated);
    }
  }

  final class Collection {
    final PortalShotParticleProgram program;
    final List<Particle> particles = new ArrayList<>();
    final float delay;
    final int seed;
    final SimdRandom lockRandom;
    int emitted;
    float emissionAccumulator;
    int identities;
    int randomIndex;
    boolean started;
    float previousDt;
    float currentDt;

    Collection(PortalShotParticleProgram.Child child) {
      program = child.program();
      delay = child.delay();
      seed = System.identityHashCode(this) + (int) (System.nanoTime() / 1_000_000L);
      lockRandom = new SimdRandom(seed);
    }

    float random() {
      return SourceRandomTable.value(seed + randomIndex++);
    }

    float random(Particle particle, int offset) {
      return SourceRandomTable.value(seed + ((seed + particle.id) & 4095) + offset);
    }

    float range(float low, float high) {
      return low + (high - low) * random();
    }

    void advance(float previous, float current) {
      if (current <= delay) return;
      float local = current - delay;
      float dt = current - Math.max(previous, delay);
      currentDt = dt;
      Vec3 cp = controlPoint(current);
      Vec3 previousCp = controlPoint(Math.max(delay, previous));
      if (!started) {
        started = true;
        float[] births = new float[program.initialCount];
        Vec3[] origins = new Vec3[program.initialCount];
        Arrays.fill(origins, controlPoint(delay));
        emit(births, origins);
      }
      // Minecraft flight times can exceed the native core's fixed emission window.
      float endEmission =
          Math.min(
              stopTime - delay,
              program.rope && program.emissionDuration > 0.0F
                  ? program.emissionDuration
                  : Float.POSITIVE_INFINITY);
      float emissionStart = Math.max(0.0F, local - dt);
      float emissionEnd = Math.min(local, endEmission);
      if (emissionEnd > emissionStart) {
        float interval = emissionEnd - emissionStart;
        emissionAccumulator += interval * program.rate;
        int requested = (int) Math.floor(emissionAccumulator) - emitted;
        emitted += requested;
        int count = Math.min(requested, program.limit - particles.size());
        if (count > 0) {
          float spacing = interval / count;
          float birth = emissionStart + spacing;
          float[] births = new float[count];
          Vec3[] origins = new Vec3[count];
          for (int i = 0; i < count; i++) {
            births[i] = Math.min(birth, emissionEnd);
            origins[i] = previousCp.lerp(cp, Math.clamp((birth - (local - dt)) / dt, 0.0F, 1.0F));
            birth += spacing;
          }
          emit(births, origins);
        }
      }
      for (int i = 0; i < program.operators.size(); i++) {
        var operator = program.operators.get(i);
        operator.action().apply(this, local, dt, cp, previousCp, i * 17);
        if (operator.decay()) particles.removeIf(particle -> local - particle.birth >= particle.life);
      }
      previousDt = dt;
    }

    void emit(float[] births, Vec3[] origins) {
      int first = particles.size();
      for (int i = 0; i < births.length; i++)
        particles.add(new Particle(identities++, births[i], origins[i], program.initialRadius));

      for (var initializer : program.initializers) {
        for (int i = 0; i < births.length; i++)
          initializer.initialize(this, particles.get(first + i), origins[i]);
      }
      for (int i = first; i < particles.size(); i++) {
        Particle particle = particles.get(i);
        particle.initialAlpha = particle.alpha;
        particle.initialRadius = particle.radius;
      }
    }

    void tint(Particle particle, Vector3f low, Vector3f high, Vector3f outLow, Vector3f outHigh) {
      particle.rgb
          .set(
              remap(color.x, low.x, high.x, outLow.x, outHigh.x),
              remap(color.y, low.y, high.y, outLow.y, outHigh.y),
              remap(color.z, low.z, high.z, outLow.z, outHigh.z))
          .mul(tintScale);
    }

    Vector3f local(Vec3 position) {
      Vec3 value = position.subtract(start);
      return new Vector3f((float) value.x, (float) value.y, (float) value.z);
    }
  }

  static final class SimdRandom {
    private final float[][] state = new float[55][4];
    private int first = 23;
    private int second = 54;

    SimdRandom(int seed) {
      for (float[] vector : state) {
        for (int lane = 0; lane < 4; lane++) {
          vector[lane] = (seed >>> 16) / 65536.0F;
          seed = 0xBB40E62D - seed * 0x44BF19D3;
        }
      }
    }

    float[] next() {
      float[] result = new float[4];
      for (int lane = 0; lane < 4; lane++) {
        float value = state[first][lane] + state[second][lane];
        if (value >= 1.0F) value -= 1.0F;
        result[lane] = value;
        state[second][lane] = value;
      }
      first = first == 0 ? 54 : first - 1;
      second = second == 0 ? 54 : second - 1;
      return result;
    }
  }

  final class Particle {
    final int id;
    final float birth;
    final Vector3f position;
    final Vector3f previous;
    final Vector3f rgb = new Vector3f(1.0F);
    float life;
    float radius;
    float initialRadius;
    float alpha = 1.0F;
    float initialAlpha;
    float alpha2 = 1.0F;

    Particle(int id, float birth, Vec3 origin, float radius) {
      this.id = id;
      this.birth = birth;
      Vec3 value = origin.subtract(start);
      position = new Vector3f((float) value.x, (float) value.y, (float) value.z);
      previous = new Vector3f(position);
      this.radius = radius;
    }

    void scalar(int field, float value, boolean multiply) {
      switch (field) {
        case 1 -> life = multiply ? life * value : value;
        case 3 -> radius = multiply ? radius * value : value;
        case 7 -> alpha = multiply ? alpha * value : value;
        case 16 -> alpha2 = multiply ? alpha2 * value : value;
        default ->
            throw new IllegalArgumentException("Unsupported projectile scalar field " + field);
      }
    }

    Vector3f rendered(Vec3 camera) {
      Vec3 relative = start.subtract(camera);
      return new Vector3f(position).add((float) relative.x, (float) relative.y, (float) relative.z);
    }
  }
}
