package dev.portalix.client.particle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.joml.Vector3f;

final class ParticleTrackSampler {
  private final Track[][] intervals;
  private final List<ParticleRecording.Frame> frames;
  private final List<MutableParticle> pool = new ArrayList<>();
  private final List<ParticleRecording.Attributes> output = new ArrayList<>();

  ParticleTrackSampler(ParticleRecording clip, Map<Long, Float> births) {
    this.frames = clip.recordedFrames();
    this.intervals = new Track[this.frames.size() - 1][];
    int maximum = 0;

    for (int f = 0; f < this.intervals.length; f++) {
      ParticleRecording.Frame first = this.frames.get(f);
      ParticleRecording.Frame next = this.frames.get(f + 1);
      Map<String, ParticleRecording.Track> following = new LinkedHashMap<>();

      for (ParticleRecording.Track track : next.tracks()) {
        following.put(track.system(), track);
      }

      List<Track> compiled = new ArrayList<>();

      for (ParticleRecording.Track track : first.tracks()) {
        ParticleRecording.Track second = following.remove(track.system());
        Map<Long, ParticleRecording.Particle> particles = new LinkedHashMap<>();
        if (second != null) {
          for (ParticleRecording.Particle particle : second.particles()) {
            particles.put(particle.id(), particle);
          }
        }

        List<Pair> pairs = new ArrayList<>();

        for (ParticleRecording.Particle particle : track.particles()) {
          ParticleRecording.Particle other = particles.remove(particle.id());
          pairs.add(pair(particle, other, births));
        }

        for (ParticleRecording.Particle particle : particles.values()) {
          pairs.add(pair(null, particle, births));
        }

        compiled.add(new Track(track.system(), pairs.toArray(Pair[]::new)));
        maximum = Math.max(maximum, pairs.size());
      }

      for (ParticleRecording.Track track : following.values()) {
        Pair[] pairs =
            track.particles().stream().map(p -> pair(null, p, births)).toArray(Pair[]::new);
        compiled.add(new Track(track.system(), pairs));
        maximum = Math.max(maximum, pairs.length);
      }

      this.intervals[f] = compiled.toArray(Track[]::new);
    }

    for (int i = 0; i < maximum; i++) {
      this.pool.add(new MutableParticle());
    }
  }

  private static Pair pair(
      ParticleRecording.Particle a, ParticleRecording.Particle b, Map<Long, Float> births) {
    return new Pair(
        a,
        b,
        a == null ? null : a.weights(),
        b == null ? null : b.weights(),
        births.get(a == null ? b.id() : a.id()));
  }

  private static float lerp(float a, float b, float t) {
    return a + (b - a) * t;
  }

  void sample(
      float time,
      String system,
      long identityOffset,
      float cycleOffset,
      float minimumBirth,
      float maximumBirth,
      float lastLocalBirth,
      Consumer<List<ParticleRecording.Attributes>> visitor) {
    float at = Math.clamp(time, 0.0F, this.frames.getLast().time());
    int lo = 0;
    int hi = this.frames.size() - 1;

    while (lo + 1 < hi) {
      int mid = (lo + hi) / 2;
      if (this.frames.get(mid).time() <= at) {
        lo = mid;
      } else {
        hi = mid;
      }
    }

    float t =
        (at - this.frames.get(lo).time())
            / (this.frames.get(hi).time() - this.frames.get(lo).time());

    for (Track track : this.intervals[lo]) {
      if (track.system().equals(system)) {
        this.output.clear();

        for (Pair pair : track.pairs()) {
          ParticleRecording.Particle a = pair.a();
          ParticleRecording.Particle b = pair.b();
          ParticleRecording.Particle identity = a == null ? b : a;
          float birth = pair.birth();
          if (birth >= minimumBirth
              && birth + cycleOffset <= maximumBirth
              && birth < lastLocalBirth) {
            MutableParticle p = this.pool.get(this.output.size());
            p.id = identity.id() + identityOffset;
            if (a != null && b != null) {
              for (int i = 0; i < 4; i++) {
                p.weights[i] = lerp(pair.aw()[i], pair.bw()[i], t);
              }

              p.radius = lerp(a.radius(), b.radius(), t);
              p.red = lerp(a.red(), b.red(), t);
              p.green = lerp(a.green(), b.green(), t);
              p.blue = lerp(a.blue(), b.blue(), t);
              p.alpha = lerp(a.alpha(), b.alpha(), t);
              p.rotation = lerp(a.rotation(), b.rotation(), t);
              p.sequence = t < 0.5F ? a.sequence() : b.sequence();
              p.age = lerp(a.age(), b.age(), t);
            } else {
              ParticleRecording.Particle source = identity;
              float[] weights = a == null ? pair.bw() : pair.aw();
              System.arraycopy(weights, 0, p.weights, 0, 4);
              p.radius = source.radius();
              p.red = source.red();
              p.green = source.green();
              p.blue = source.blue();
              p.alpha = source.alpha() * (a == null ? t : 1.0F - t);
              p.rotation = source.rotation();
              p.sequence = source.sequence();
              p.age = source.age();
            }

            this.output.add(p);
          }
        }

        if (!this.output.isEmpty()) {
          visitor.accept(this.output);
        }
      }
    }
  }

  static final class MutableParticle implements ParticleRecording.Attributes {
    long id;
    final float[] weights = new float[4];
    float radius;
    float red;
    float green;
    float blue;
    float alpha;
    float rotation;
    float age;
    int sequence;

    @Override
    public long id() {
      return this.id;
    }

    @Override
    public float radius() {
      return this.radius;
    }

    @Override
    public float red() {
      return this.red;
    }

    @Override
    public float green() {
      return this.green;
    }

    @Override
    public float blue() {
      return this.blue;
    }

    @Override
    public float alpha() {
      return this.alpha;
    }

    @Override
    public float rotation() {
      return this.rotation;
    }

    @Override
    public int sequence() {
      return this.sequence;
    }

    @Override
    public float age() {
      return this.age;
    }

    @Override
    public Vector3f position(Vector3f[] points, Vector3f destination) {
      destination.zero();

      for (int i = 0; i < 4; i++) {
        destination.fma(this.weights[i], points[i]);
      }

      return destination;
    }
  }

  private record Pair(
      ParticleRecording.Particle a,
      ParticleRecording.Particle b,
      float[] aw,
      float[] bw,
      float birth) {}

  private record Track(String system, Pair[] pairs) {
    Track {
      int separator = system.indexOf('#');
      if (separator >= 0) system = system.substring(0, separator);
    }
  }
}
