package dev.portalix.client.particle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

final class ParticleRecordingPlayer {
  private final float duration;
  private final ParticleTrackSampler sampler;
  private final Set<String> systems;
  private final float loopStart;
  private final float loopEnd;
  private final float period;
  private final float tail;
  private final long identities;

  public ParticleRecordingPlayer(ParticleRecording source, Set<String> ropes, float loopStart) {
    if (!(Float.isFinite(loopStart) && loopStart >= 0.0F && loopStart < source.duration())) {
      throw new IllegalArgumentException("Invalid Source recording loop start");
    }
    this.loopStart = loopStart;
    Map<Long, Float> births = new HashMap<>();
    Map<String, Map<Long, Life>> live = new HashMap<>();
    Map<String, List<Group>> groups = new HashMap<>();
    List<ParticleRecording.Frame> frames = new ArrayList<>();
    long identity = 0L;
    float longest = 0.0F;
    int frameIndex = 0;

    for (ParticleRecording.Frame frame : source.recordedFrames()) {
      Map<String, List<ParticleRecording.Particle>> tracks = new LinkedHashMap<>();

      for (ParticleRecording.Track track : frame.tracks()) {
        String system = track.system().split("#", 2)[0];
        Map<Long, Life> previous = live.computeIfAbsent(track.system(), key -> new HashMap<>());

        for (ParticleRecording.Particle particle : track.particles()) {
          Life life = previous.get(particle.id());
          float elapsed = life == null ? 0.0F : frame.time() - life.observed();
          if (life != null
              && life.frame() == frameIndex - 1
              && !(particle.age() < life.age() - 0.001F)
              && !(Math.abs(particle.age() - life.age() - elapsed) > 0.02F)) {
            life =
                new Life(
                    life.id(), life.birth(), particle.age(), frame.time(), frameIndex, life.group());
          } else {
            float birth = frame.time() - particle.age();
            String group = track.system();
            if (ropes.contains(system)) {
              List<Group> candidates =
                  groups.computeIfAbsent(track.system(), key -> new ArrayList<>());
              Group found = null;

              for (Group candidate : candidates) {
                if (Math.abs(candidate.birth() - birth) < 0.02F) {
                  found = candidate;
                  break;
                }
              }

              if (found == null) {
                found = new Group(birth, track.system() + "#life" + candidates.size());
                candidates.add(found);
              }

              group = found.name();
            }

            life = new Life(++identity, birth, particle.age(), frame.time(), frameIndex, group);
            births.put(life.id(), birth);
          }

          previous.put(particle.id(), life);
          longest = Math.max(longest, particle.age());
          tracks
              .computeIfAbsent(life.group(), key -> new ArrayList<>())
              .add(
                  new ParticleRecording.Particle(
                      life.id(),
                      particle.weights(),
                      particle.radius(),
                      particle.red(),
                      particle.green(),
                      particle.blue(),
                      particle.alpha(),
                      particle.rotation(),
                      particle.sequence(),
                      particle.age()));
        }
      }

      List<ParticleRecording.Track> values = new ArrayList<>();
      tracks.forEach(
          (name, particles) ->
              values.add(new ParticleRecording.Track(name, particles)));
      frames.add(new ParticleRecording.Frame(frame.time(), values));
      frameIndex++;
    }

    ParticleRecording indexed = new ParticleRecording(frames);
    this.duration = indexed.duration();
    this.sampler = new ParticleTrackSampler(indexed, births);
    Set<String> names = new LinkedHashSet<>();

    for (ParticleRecording.Frame frame : frames) {
      for (ParticleRecording.Track track : frame.tracks()) {
        names.add(track.system().split("#", 2)[0]);
      }
    }

    this.systems = Set.copyOf(names);
    this.identities = identity;
    this.tail = longest + 0.1F;
    this.loopEnd = source.duration() - this.tail;
    this.period = this.loopEnd - loopStart;
    if (this.identities == 0L || this.period <= this.tail) {
      throw new IllegalArgumentException(
          "Source recording needs a complete steady section and lifetime tail");
    }
  }

  public float tailDuration() {
    return this.tail;
  }

  Set<String> systems() {
    return this.systems;
  }

  void sampleSystem(
      float age, float stop, String system, Consumer<List<ParticleRecording.Attributes>> visitor) {
    age = Math.max(0.0F, age);
    int latest = age < this.loopEnd ? 0 : 1 + (int) Math.floor((age - this.loopEnd) / this.period);
    int first = Math.max(0, latest - (int) Math.ceil(this.tail / this.period));

    for (int cycle = first; cycle <= latest; cycle++) {
      float offset = cycle * this.period;
      float local = age - offset;
      if (local <= this.duration) {
        this.sampler.sample(
            local,
            system,
            cycle * this.identities,
            offset,
            cycle == 0 ? Float.NEGATIVE_INFINITY : this.loopStart,
            Math.min(age, stop) + 1.0E-5F,
            this.loopEnd,
            visitor);
      }
    }
  }

  private record Group(float birth, String name) {}

  private record Life(long id, float birth, float age, float observed, int frame, String group) {}
}
