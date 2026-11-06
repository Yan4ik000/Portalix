package dev.portalix.client.particle;

import static dev.portalix.client.particle.ParticleDefinitionReader.name;
import static dev.portalix.client.particle.ParticleDefinitionReader.scalar;
import static dev.portalix.client.particle.ParticleDefinitionReader.vector;
import static dev.portalix.client.particle.SourceParticleKinematics.fade;
import static dev.portalix.client.particle.SourceParticleKinematics.remap;
import static dev.portalix.client.particle.SourceParticleKinematics.sourceSine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.portalix.client.particle.PortalShotParticleSystem.Collection;
import dev.portalix.client.particle.PortalShotParticleSystem.Particle;
import dev.portalix.resource.SourceUnits;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

final class PortalShotParticleProgram {
  private static final float UNIT = (float) SourceUnits.SOURCE_UNIT_BLOCKS;

  interface Initializer {
    void initialize(Collection collection, Particle particle, Vec3 origin);
  }

  private interface ParticleOperator {
    void apply(Collection collection, Particle particle, float time, float dt, Vec3 control, int offset);
  }

  interface Operator {
    void apply(Collection collection, float time, float dt, Vec3 control, Vec3 previousControl, int offset);
  }

  private record Initialization(boolean modifier, Initializer action) {}

  record Operation(boolean decay, Operator action) {}

  record Child(PortalShotParticleProgram program, float delay) {}

  record Library(float maximumStep, List<Child> children) {
    Library(JsonObject systems) {
      this(
          scalar(systems.getAsJsonObject("portal_projectile_stream"), "maximum time step"),
          children(systems));
    }

    private static List<Child> children(JsonObject systems) {
      var children = new ArrayList<Child>();
      for (JsonElement child :
          systems.getAsJsonObject("portal_projectile_stream").getAsJsonArray("children")) {
        JsonObject value = child.getAsJsonObject();
        var program =
            new PortalShotParticleProgram(
                systems.getAsJsonObject(value.get("child").getAsString()));
        children.add(new Child(program, scalar(value, "delay")));
      }
      return List.copyOf(children);
    }
  }

  final float rate;
  final float emissionDuration;
  final int limit;
  final int initialCount;
  final float initialRadius;
  final int subdivisions;
  final boolean rope;
  final float texelScale;
  final List<Initializer> initializers;
  final List<Operation> operators;

  private PortalShotParticleProgram(JsonObject definition) {
    limit = (int) scalar(definition, "max_particles");
    initialCount = (int) scalar(definition, "initial_particles");
    initialRadius = scalar(definition, "radius") * UNIT;
    JsonObject emitter = definition.getAsJsonArray("emitters").get(0).getAsJsonObject();
    rate = scalar(emitter, "emission_rate");
    emissionDuration = scalar(emitter, "emission_duration");
    var initialization = new ArrayList<Initialization>();
    for (JsonElement value : definition.getAsJsonArray("initializers")) {
      JsonObject initializer = value.getAsJsonObject();
      boolean modifier =
          switch (name(initializer)) {
            case "Remap Particle Count to Scalar", "Position Modify Warp Random", "remap initial scalar" -> true;
            default -> false;
          };
      initialization.add(new Initialization(modifier, compileInitializer(initializer)));
    }
    initialization.sort(Comparator.comparing(Initialization::modifier));
    initializers = initialization.stream().map(Initialization::action).toList();
    var operations = new ArrayList<Operation>();
    for (JsonElement value : definition.getAsJsonArray("operators")) {
      JsonObject operator = value.getAsJsonObject();
      boolean decay =
          name(operator).equals("Lifespan Decay") || name(operator).equals("Alpha Fade and Decay");
      operations.add(new Operation(decay, compileOperator(operator)));
    }
    operators = List.copyOf(operations);
    JsonObject renderer = definition.getAsJsonArray("renderers").get(0).getAsJsonObject();
    rope = name(renderer).equals("render_rope");
    subdivisions = rope ? (int) scalar(renderer, "subdivision_count") : 0;
    texelScale = rope ? scalar(renderer, "texel_size") / UNIT : 0.0F;
  }

  private static Initializer compileInitializer(JsonObject fields) {
    return switch (name(fields)) {
      case "Position Within Sphere Random" -> {
        float minimumDistance = scalar(fields, "distance_min");
        float maximumDistance = scalar(fields, "distance_max");
        Vector3f minimumSpeed = vector(fields, "speed_in_local_coordinate_system_min");
        Vector3f maximumSpeed = vector(fields, "speed_in_local_coordinate_system_max");
        yield (collection, particle, origin) -> {
          float cosine = 1.0F - 2.0F * (collection.random() * 0.9999F + 0.0001F);
          double azimuth = (collection.random() * 0.9999F + 0.0001F) * Math.PI * 2.0;
          float radial = (float) Math.pow(collection.random() * 0.9999F + 0.0001F, 0.3333333432674408);
          float distance = minimumDistance + (maximumDistance - minimumDistance) * radial;
          float ring = (float) Math.sqrt(1.0F - cosine * cosine);
          particle.position.add(
              (float) Math.cos(azimuth) * ring * distance * UNIT,
              cosine * distance * UNIT,
              (float) Math.sin(azimuth) * ring * distance * UNIT);
          float dt = collection.previousDt == 0.0F ? collection.currentDt : collection.previousDt;
          particle.previous.set(particle.position).sub(
              collection.range(minimumSpeed.x, maximumSpeed.x) * UNIT * dt,
              collection.range(minimumSpeed.z, maximumSpeed.z) * UNIT * dt,
              collection.range(minimumSpeed.y, maximumSpeed.y) * UNIT * dt);
        };
      }
      case "Lifetime Random" -> {
        float minimum = scalar(fields, "lifetime_min"), maximum = scalar(fields, "lifetime_max");
        yield (collection, particle, origin) -> particle.life = collection.range(minimum, maximum);
      }
      case "Alpha Random" -> {
        float minimum = scalar(fields, "alpha_min"), maximum = scalar(fields, "alpha_max");
        yield (collection, particle, origin) ->
            particle.alpha = collection.range(minimum, maximum) / 255.0F;
      }
      case "remap initial scalar", "Remap Particle Count to Scalar" -> {
        boolean count = name(fields).equals("Remap Particle Count to Scalar");
        if (!count && (int) scalar(fields, "input field") != 8)
          throw new IllegalArgumentException("Unsupported projectile scalar input");
        float inputMinimum = scalar(fields, "input minimum");
        float inputMaximum = scalar(fields, "input maximum");
        float outputMinimum = scalar(fields, "output minimum");
        float outputMaximum = scalar(fields, "output maximum");
        int field = (int) scalar(fields, "output field");
        boolean multiply = scalar(fields, "output is scalar of initial random range") != 0.0F;
        yield (collection, particle, origin) ->
            particle.scalar(
                field,
                remap(
                    count ? particle.id : particle.birth,
                    inputMinimum, inputMaximum, outputMinimum, outputMaximum),
                multiply);
      }
      case "Position Modify Warp Random" -> {
        boolean count = scalar(fields, "use particle count instead of time") != 0.0F;
        Vector3f low = vector(fields, "warp min"), high = vector(fields, "warp max");
        float start = scalar(fields, "warp transition start time");
        float duration = scalar(fields, "warp transition time (treats min/max as start/end sizes)");
        yield (collection, particle, origin) -> {
          float time = count ? particle.id : particle.birth;
          float progress = Math.clamp((time - start) / duration, 0.0F, 1.0F);
          Vector3f scale = new Vector3f(low).lerp(high, progress);
          Vector3f center = collection.local(origin);
          particle.position.sub(center).mul(scale).add(center);
          particle.previous.sub(center).mul(scale).add(center);
        };
      }
      case "Remap Control Point to Vector" -> {
        var tint = compileTint(fields);
        yield (collection, particle, origin) -> tint.accept(collection, particle);
      }
      default -> throw new IllegalArgumentException("Unsupported projectile initializer " + fields);
    };
  }

  private static Operator compileOperator(JsonObject fields) {
    if (name(fields).equals("Movement Lock to Control Point")) {
      float startMinimum = scalar(fields, "start_fadeout_min");
      float startMaximum = scalar(fields, "start_fadeout_max");
      float endMinimum = scalar(fields, "end_fadeout_min");
      float endMaximum = scalar(fields, "end_fadeout_max");
      return (collection, time, dt, control, previousControl, offset) -> {
        for (int block = 0; block < collection.particles.size(); block += 4) {
          float[] starts = collection.lockRandom.next();
          float[] ends = collection.lockRandom.next();
          for (int lane = 0; lane < 4 && block + lane < collection.particles.size(); lane++) {
            Particle particle = collection.particles.get(block + lane);
            float fadeStart = startMinimum + (startMaximum - startMinimum) * starts[lane];
            float fadeEnd = endMinimum + (endMaximum - endMinimum) * ends[lane];
            float age = time - particle.birth;
            float weight = remap(age / particle.life, fadeStart, fadeEnd, 1.0F, 0.0F)
                * Math.clamp(age / dt, 0.0F, 1.0F);
            Vec3 delta = control.subtract(previousControl).scale(weight);
            particle.position.add((float) delta.x, (float) delta.y, (float) delta.z);
            particle.previous.add((float) delta.x, (float) delta.y, (float) delta.z);
          }
        }
      };
    }
    ParticleOperator action = compileParticleOperator(fields);
    return (collection, time, dt, control, previousControl, offset) -> {
      for (Particle particle : collection.particles)
        action.apply(collection, particle, time, dt, control, offset);
    };
  }

  private static ParticleOperator compileParticleOperator(JsonObject fields) {
    return switch (name(fields)) {
      case "Movement Basic" -> {
        Vector3f gravity = vector(fields, "gravity").mul(UNIT);
        float drag = scalar(fields, "drag");
        yield (collection, particle, time, dt, control, offset) -> {
          float momentum =
              SourceParticleKinematics.momentumScale(
                  drag, dt, collection.previousDt == 0.0F ? dt : collection.previousDt);
          float x =
              SourceParticleKinematics.movement(
                  particle.position.x, particle.previous.x, gravity.x, dt, momentum);
          float y =
              SourceParticleKinematics.movement(
                  particle.position.y, particle.previous.y, gravity.z, dt, momentum);
          float z =
              SourceParticleKinematics.movement(
                  particle.position.z, particle.previous.z, gravity.y, dt, momentum);
          particle.previous.set(particle.position);
          particle.position.set(x, y, z);
        };
      }
      case "Radius Scale" -> {
        float start = scalar(fields, "start_time"), end = scalar(fields, "end_time");
        float minimum = scalar(fields, "radius_start_scale"), maximum = scalar(fields, "radius_end_scale");
        yield (collection, particle, time, dt, control, offset) ->
            particle.radius =
                particle.initialRadius
                    * remap((time - particle.birth) / particle.life, start, end, minimum, maximum);
      }
      case "Oscillate Vector" -> {
        float startMinimum = scalar(fields, "start time min");
        float startMaximum = scalar(fields, "start time max");
        float endMinimum = scalar(fields, "end time min");
        float endMaximum = scalar(fields, "end time max");
        Vector3f low = vector(fields, "oscillation rate min"), high = vector(fields, "oscillation rate max");
        Vector3f frequencyLow = vector(fields, "oscillation frequency min");
        Vector3f frequencyHigh = vector(fields, "oscillation frequency max");
        float multiplier = scalar(fields, "oscillation multiplier");
        float phase = scalar(fields, "oscillation start phase");
        boolean proportional = scalar(fields, "proportional 0/1") != 0.0F;
        yield (collection, particle, time, dt, control, offset) -> {
          float life = (time - particle.birth) / particle.life;
          float start =
              startMinimum + (startMaximum - startMinimum) * collection.random(particle, offset + 11);
          float end = endMinimum + (endMaximum - endMinimum) * collection.random(particle, offset + 12);
          if (!(life >= start && life < end)) return;
          float clock = proportional ? life : time;
          for (int axis = 0; axis < 3; axis++) {
            int rateOffset = axis == 0 ? 3 : axis == 1 ? 7 : 9;
            int frequencyOffset = axis == 0 ? 8 : axis == 1 ? 12 : 15;
            float rate =
                low.get(axis)
                    + (high.get(axis) - low.get(axis))
                        * collection.random(particle, offset + rateOffset);
            float frequency =
                frequencyLow.get(axis)
                    + (frequencyHigh.get(axis) - frequencyLow.get(axis))
                        * collection.random(particle, offset + frequencyOffset);
            int component = axis == 1 ? 2 : axis == 2 ? 1 : 0;
            particle.position.setComponent(component, particle.position.get(component)
                + rate * dt * UNIT * sourceSine(frequency * clock * multiplier + phase));
          }
        };
      }
      case "Lifespan Decay" -> (collection, particle, time, dt, control, offset) -> {};
      case "Alpha Fade and Decay" -> {
        float inStart = scalar(fields, "start_fade_in_time"), inEnd = scalar(fields, "end_fade_in_time");
        float outStart = scalar(fields, "start_fade_out_time"), outEnd = scalar(fields, "end_fade_out_time");
        float startAlpha = scalar(fields, "start_alpha"), endAlpha = scalar(fields, "end_alpha");
        yield (collection, particle, time, dt, control, offset) -> {
          float life = (time - particle.birth) / particle.life;
          float alpha = life < inEnd ? fade(life, inStart, inEnd, startAlpha, 1.0F)
              : life >= outStart ? fade(life, outStart, outEnd, 1.0F, endAlpha) : 1.0F;
          particle.alpha = particle.initialAlpha * alpha;
        };
      }
      case "Remap Distance to Control Point to Scalar" -> {
        float inputMinimum = scalar(fields, "distance minimum");
        float inputMaximum = scalar(fields, "distance maximum");
        float outputMinimum = scalar(fields, "output minimum");
        float outputMaximum = scalar(fields, "output maximum");
        int field = (int) scalar(fields, "output field");
        yield (collection, particle, time, dt, control, offset) -> {
          float distance = particle.position.distance(collection.local(control)) / UNIT;
          particle.scalar(
              field,
              remap(distance, inputMinimum, inputMaximum, outputMinimum, outputMaximum),
              false);
        };
      }
      case "Remap Control Point to Vector" -> {
        var tint = compileTint(fields);
        yield (collection, particle, time, dt, control, offset) -> tint.accept(collection, particle);
      }
      default -> throw new IllegalArgumentException("Unsupported projectile operator " + fields);
    };
  }

  private static BiConsumer<Collection, Particle> compileTint(JsonObject fields) {
    Vector3f low = vector(fields, "input minimum"), high = vector(fields, "input maximum");
    Vector3f outLow = vector(fields, "output minimum"), outHigh = vector(fields, "output maximum");
    return (collection, particle) -> collection.tint(particle, low, high, outLow, outHigh);
  }
}
