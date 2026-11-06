package dev.portalix.client.particle;

import static dev.portalix.client.particle.ParticleDefinitionReader.scalar;
import static dev.portalix.client.particle.SourceParticleProgram.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.portalix.item.PortalixVariant;
import dev.portalix.resource.ModAssetLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SourceParticleSystem {
  private static final Library SYSTEMS =
      new Library(ModAssetLoader.json("/assets/portalix/portals/runtime.json"));
  final List<Collection> collections = new ArrayList<>();
  final Vector3f color;
  private final int seed;
  private final List<String> roots;
  private final Library systems;
  private final double warmup;
  private double clock;
  private double requestedTime;
  private double timeOffset;
  private long revision;
  private float endcap = Float.POSITIVE_INFINITY;
  private final Vector3f[] controls = new Vector3f[32];
  private final Quaternionf[] rotations = new Quaternionf[32];
  static final float STEP = 1.0F / 60;

  public static void loadResources() {
    SourceRandomTable.value(0);
    PortalNoiseField.sample(0, 0, 0);
  }

  public SourceParticleSystem(PortalixVariant variant, boolean orange, long identity, double age) {
    this(
        SYSTEMS,
        List.of(orange ? "portal_edge_reverse" : "portal_edge"),
        SourceParticleColor.portalPalette(variant, orange),
        (int) (identity ^ (identity >>> 32)),
        age);
  }

  SourceParticleSystem(Library systems, List<String> roots, Vector3f color, int seed, double age) {
    this.systems = systems;
    this.roots = List.copyOf(roots);
    this.color = new Vector3f(color);
    this.seed = seed & 4095;
    for (int i = 0; i < controls.length; i++) {
      controls[i] = new Vector3f();
      rotations[i] = new Quaternionf();
    }
    initializeControls();
    for (String root : roots) add(root, 0);
    warmup = warmupSeconds();
    if (age > 0) warmStart(age);
  }

  private void initializeControls() {
    controls[7].set(color);
    if (roots.stream().noneMatch(root -> root.startsWith("portal_edge"))) {
      controls[2].set(color);
      if (!roots.contains("portal_close")) controls[1].set(color);
    }
  }

  private void add(String name, float birth) {
    Library.Definition definition = systems.definitions.get(name);
    if (definition.program() != null)
      collections.add(new Collection(this, name, birth, definition.program()));
    for (Library.Child child : definition.children()) add(child.name(), birth + child.delay());
  }

  public void close() {
    if (Float.isFinite(endcap)) return;
    endcap = (float) requestedTime;
    revision++;
  }

  static SourceParticleSystem closing(PortalixVariant variant, boolean orange, long identity) {
    var system =
        new SourceParticleSystem(
            SYSTEMS,
            List.of("portal_close"),
            SourceParticleColor.portalPalette(variant, orange),
            (int) (identity ^ (identity >>> 32)),
            0);
    for (Collection collection : system.collections) collection.emit(0);
    return system;
  }

  public long revision() {
    return revision;
  }

  public void advance(double seconds) {
    if (!Float.isFinite(endcap) && seconds > warmup) {
      warmStart(timeOffset + requestedTime + seconds);
      return;
    }
    requestedTime += Math.max(0, seconds);

    if (Float.isFinite(endcap))
      requestedTime = Math.min(requestedTime, endcap + maximumLifetime() + STEP);
    simulate();
  }

  private void simulate() {
    while (clock + STEP <= requestedTime + 1e-8) {
      clock += STEP;
      for (Collection collection : collections) collection.step((float) clock);
      revision++;
    }
  }

  private double warmupSeconds() {
    double start = 0;
    for (Collection collection : collections)
      start = Math.max(start, collection.program.emissionStart);
    return start + maximumLifetime() + STEP;
  }

  private void warmStart(double age) {
    long skippedSteps = Math.max(0, (long) Math.floor((age - warmup) / STEP));
    double duration = Math.max(0, age) - skippedSteps * (double) STEP;

    collections.clear();
    for (String root : roots) add(root, 0);
    clock = requestedTime = 0;
    timeOffset = skippedSteps * (double) STEP;
    revision++;
    for (int i = 0; i < controls.length; i++) {
      controls[i].zero();
      rotations[i].identity();
    }
    initializeControls();
    if (skippedSteps > 0) {
      Map<Integer, Quaternionf> increments = new HashMap<>();
      for (Collection collection : collections)
        for (ControlRotation rotation : collection.program.controlRotations)
          increments
              .computeIfAbsent(rotation.point(), ignored -> new Quaternionf())
              .rotateAxis((float) Math.toRadians(STEP * rotation.rate()), rotation.axis());
      increments.forEach(
          (point, increment) -> rotations[point].set(rotationPower(increment, skippedSteps - 1)));
      for (Collection collection : collections)
        collection.program.controlOperators.forEach(operator -> operator.accept(collection));
    }
    requestedTime = duration;
    simulate();
  }

  private static Quaternionf rotationPower(Quaternionf increment, long steps) {
    Quaternionf result = new Quaternionf(), power = new Quaternionf(increment);
    while (steps > 0) {
      if ((steps & 1) != 0) result.mul(power).normalize();
      power.mul(new Quaternionf(power)).normalize();
      steps >>>= 1;
    }
    return result;
  }

  private double maximumLifetime() {
    double maximum = 0;
    for (Collection collection : collections)
      maximum = Math.max(maximum, collection.program.lifetime);
    return maximum;
  }

  static final class Library {
    record Child(String name, float delay) {}

    record Definition(SourceParticleProgram program, List<Child> children) {}

    final Map<String, Definition> definitions;

    Library(JsonObject source) {
      var compiled = new HashMap<String, Definition>();
      source
          .asMap()
          .forEach(
              (name, value) -> {
                JsonObject definition = value.getAsJsonObject();
                var children = new ArrayList<Child>();
                for (JsonElement child : definition.getAsJsonArray("children")) {
                  JsonObject entry = child.getAsJsonObject();
                  children.add(
                      new Child(
                          entry.get("child").getAsString(),
                          entry.has("delay") ? scalar(entry, "delay") : 0));
                }
                SourceParticleProgram program =
                    definition.getAsJsonArray("renderers").isEmpty()
                        ? null
                        : new SourceParticleProgram(definition);
                compiled.put(name, new Definition(program, List.copyOf(children)));
              });
      definitions = Map.copyOf(compiled);
    }
  }

  static final class Collection {
    final SourceParticleSystem simulation;
    final String name;
    final SourceParticleProgram program;
    final List<Particle> particles = new ArrayList<>();
    final Vector3f acceleration = new Vector3f();
    final Vector3f nextPosition = new Vector3f();
    final Vector3f forceDirection = new Vector3f();
    final float birth;
    final boolean edge;
    int emitted, randomIndex;
    float time;

    double timelineOffset() {
      return simulation.timeOffset;
    }

    Vector3f controlPoint(int index) {
      return simulation.controls[index];
    }

    Quaternionf controlRotation(int index) {
      return simulation.rotations[index];
    }

    double timeline() {
      return simulation.timeOffset + time;
    }

    Collection(
        SourceParticleSystem simulation, String name, float birth, SourceParticleProgram program) {
      this.simulation = simulation;
      this.name = name;
      this.birth = birth;
      edge = name.startsWith("portal_edge");
      this.program = program;
    }

    float random() {
      return SourceRandomTable.value(simulation.seed + randomIndex++);
    }

    float range(float low, float high) {
      return low + (high - low) * random();
    }

    float random(Particle particle, int offset) {
      return SourceRandomTable.value(
          simulation.seed + ((simulation.seed + particle.id) & 4095) + offset);
    }

    Vector3f rangeVector(Vector3f a, Vector3f b) {
      return new Vector3f(range(a.x, b.x), range(a.y, b.y), range(a.z, b.z));
    }

    void step(float globalTime) {
      time = globalTime - birth;
      boolean stopped = edge && globalTime >= simulation.endcap;
      if (!stopped) emit(globalTime);
      for (int i = 0; i < program.operators.size(); i++) {
        Operator op = program.operators.get(i);
        int cap = op.endCapState();
        if (cap != -1 && (cap == 1) != stopped) continue;
        if (time < op.startTime()) continue;
        op.action().accept(this, i * 17);
        particles.removeIf(p -> p.dead);
      }
    }

    void emit(float globalTime) {
      time = globalTime - birth;
      int total =
          time < program.emissionStart
              ? 0
              : program.instantaneous
                  ? program.burst
                  : (int) Math.floor((time - program.emissionStart) * program.emissionRate);
      int first = particles.size();
      while (emitted < total) {
        int id = emitted++;
        if (particles.size() < program.maxParticles) {
          float born =
              program.instantaneous
                  ? program.emissionStart
                  : program.emissionStart + (id + 1) / program.emissionRate;
          Particle particle =
              new Particle(id, born, program.initialRadius, new Vector3f(program.initialColor));
          particle.alpha = program.initialAlpha;
          particles.add(particle);
        }
      }
      for (Initializer initializer : program.initializers) {
        for (int i = first; i < particles.size(); i++) {
          initializer.action().accept(this, particles.get(i));
        }
      }
      for (int i = first; i < particles.size(); i++) particles.get(i).capture();
    }
  }

  static final class Particle {
    final int id;
    final float born;
    final Vector3f position = new Vector3f(),
        previous = new Vector3f(),
        normal = new Vector3f(0, 0, 1),
        rgb;
    float radius, alpha = 1, alpha2 = 1, life = 1, rotation, initialRadius, initialAlpha;
    boolean dead;

    Particle(int id, float born, float radius, Vector3f color) {
      this.id = id;
      this.born = born;
      this.radius = radius;
      rgb = color;
    }

    void capture() {
      initialAlpha = alpha;
      initialRadius = radius;
    }

    float scalar(int field) {
      return switch (field) {
        case 1 -> life;
        case 3 -> radius;
        case 4 -> (float) Math.toDegrees(rotation);
        case 7 -> alpha;
        case 8 -> born;
        case 16 -> alpha2;
        default -> throw new IllegalArgumentException("Unported scalar " + field);
      };
    }

    void scalar(int field, float value, boolean multiply) {
      if (multiply) value *= scalar(field);
      switch (field) {
        case 1 -> life = value;
        case 3 -> radius = value;
        case 4 -> rotation = (float) Math.toRadians(value);
        case 7 -> alpha = Math.clamp(value, 0, 1);
        case 16 -> alpha2 = Math.clamp(value, 0, 1);
        default -> throw new IllegalArgumentException("Unported scalar " + field);
      }
    }
  }
}
