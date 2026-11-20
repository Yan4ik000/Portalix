package dev.portalix.client.particle;

import static dev.portalix.client.particle.ParticleDefinitionReader.name;
import static dev.portalix.client.particle.ParticleDefinitionReader.operators;
import static dev.portalix.client.particle.ParticleDefinitionReader.scalar;
import static dev.portalix.client.particle.ParticleDefinitionReader.vector;
import static dev.portalix.client.particle.SourceParticleKinematics.remap;
import static dev.portalix.client.particle.SourceParticleKinematics.sourceSine;
import static dev.portalix.client.particle.SourceParticleSystem.STEP;

import com.google.gson.JsonObject;
import dev.portalix.client.particle.SourceParticleSystem.Collection;
import dev.portalix.client.particle.SourceParticleSystem.Particle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class SourceParticleProgram {
  interface ParticleOperator {
    void accept(Collection collection, Particle particle, int randomOffset);
  }

  interface Oscillation {
    void apply(Collection collection, Particle particle, int randomOffset, float time);
  }

  interface ParticleForce {
    void add(Collection collection, Particle particle, Vector3f acceleration);
  }

  record DistanceConstraint(float minimum, float maximum) {}

  record Initializer(boolean modifier, BiConsumer<Collection, Particle> action) {}

  record Operator(int endCapState, float startTime, BiConsumer<Collection, Integer> action) {}

  record ControlRotation(int point, float rate, Vector3f axis) {}

  sealed interface Renderer {
    record Sprites(int orientation) implements Renderer {}

    record Rope(int subdivisions, float textureOffset, float scrollRate, float texelSize)
        implements Renderer {}

    record Trail(float maxLength, float fadeInTime) implements Renderer {}
  }

  final String material;
  final Renderer renderer;
  final List<Initializer> initializers;
  final List<Operator> operators;
  final List<ParticleForce> forces;
  final List<DistanceConstraint> constraints;
  final List<ControlRotation> controlRotations = new ArrayList<>();
  final List<Consumer<Collection>> controlOperators = new ArrayList<>();
  final float emissionStart, emissionRate, initialRadius, initialAlpha;
  final int burst, maxParticles;
  final boolean instantaneous;
  final Vector3f initialColor;
  final double lifetime;

  SourceParticleProgram(JsonObject definition) {
    material = definition.get("material").getAsString().replace('\\', '/');
    renderer = compileRenderer(definition.getAsJsonArray("renderers").get(0).getAsJsonObject());

    forces = operators(definition, "forces").stream().map(this::compileForce).toList();
    constraints =
        operators(definition, "constraints").stream()
            .map(
                operator ->
                    new DistanceConstraint(
                        scalar(operator, "minimum distance"), scalar(operator, "maximum distance")))
            .toList();
    initializers = new ArrayList<>();
    double maximum = 1;
    for (JsonObject operator : operators(definition, "initializers")) {
      boolean modifier =
          switch (name(operator)) {
            case "Position Modify Warp Random",
                "Position Modify Offset Random",
                "Remap Particle Count to Scalar",
                "Remap Control Point to Vector",
                "Remap Initial Distance to Control Point to Scalar",
                "remap initial scalar",
                "Normal Modify Offset Random",
                "Velocity Random",
                "Velocity Repulse from World" ->
                true;
            default -> false;
          };
      initializers.add(new Initializer(modifier, compileInitializer(operator)));
      if (name(operator).equals("Lifetime Random"))
        maximum = Math.max(maximum, scalar(operator, "lifetime_max"));
      if (name(operator).equals("remap initial scalar") && scalar(operator, "output field") == 1)
        maximum =
            Math.max(
                maximum,
                Math.max(scalar(operator, "output minimum"), scalar(operator, "output maximum")));
    }
    initializers.sort(Comparator.comparing(Initializer::modifier));
    lifetime = maximum;
    operators = new ArrayList<>();
    for (JsonObject operator : operators(definition, "operators")) {
      operators.add(
          new Operator(
              (int) scalar(operator, "operator end cap state"),
              scalar(operator, "operator start fadein"),
              compileOperator(operator)));
      if (name(operator).equals("Set Control Point Rotation")) {
        controlRotations.add(
            new ControlRotation(
                (int) scalar(operator, "Control Point"),
                scalar(operator, "Rotation Rate"),
                vector(operator, "Rotation Axis").normalize()));
      }
    }
    JsonObject emitter = definition.getAsJsonArray("emitters").get(0).getAsJsonObject();
    emissionStart = scalar(emitter, "emission_start_time");
    instantaneous = name(emitter).equals("emit_instantaneously");
    burst = instantaneous ? (int) scalar(emitter, "num_to_emit") : 0;
    emissionRate = instantaneous ? 0 : scalar(emitter, "emission_rate");
    maxParticles = (int) scalar(definition, "max_particles");
    initialRadius = scalar(definition, "radius");
    initialColor = vector(definition, "color").div(255);
    initialAlpha = definition.getAsJsonArray("color").get(3).getAsFloat() / 255;
  }

  private static Renderer compileRenderer(JsonObject renderer) {
    return switch (name(renderer)) {
      case "render_animated_sprites" ->
          new Renderer.Sprites((int) scalar(renderer, "orientation_type"));
      case "render_rope" ->
          new Renderer.Rope(
              (int) scalar(renderer, "subdivision_count"),
              scalar(renderer, "texture_offset"),
              scalar(renderer, "texture_scroll_rate"),
              scalar(renderer, "texel_size"));
      case "render_sprite_trail" ->
          new Renderer.Trail(
              scalar(renderer, "max length"), scalar(renderer, "length fade in time"));
      default -> throw new IllegalStateException("Unported portal renderer: " + name(renderer));
    };
  }

  private BiConsumer<Collection, Integer> compileOperator(JsonObject operator) {
    Consumer<Collection> control =
        switch (name(operator)) {
          case "Set Control Point Rotation" -> {
            int point = (int) scalar(operator, "Control Point");
            int local = (int) scalar(operator, "Local Space Control Point");
            Vector3f axis = vector(operator, "Rotation Axis").normalize();
            float radians = (float) Math.toRadians(STEP * scalar(operator, "Rotation Rate"));
            yield collection -> {
              Vector3f direction = new Vector3f(axis);
              if (local >= 0) collection.controlRotation(local).transform(direction);
              collection.controlRotation(point).rotateAxis(radians, direction).normalize();
            };
          }
          case "Set Control Point Positions" -> {
            int parent = (int) scalar(operator, "Control Point to offset positions from");
            boolean world = scalar(operator, "Set positions in world space") != 0;
            List<Consumer<Collection>> positions = new ArrayList<>();
            for (String ordinal : List.of("First", "Second", "Third", "Fourth")) {
              int point = (int) scalar(operator, ordinal + " Control Point Number");
              Vector3f location = vector(operator, ordinal + " Control Point Location");
              positions.add(
                  collection -> {
                    if (world) collection.controlPoint(point).set(location);
                    else {
                      collection.controlRotation(parent).transform(location, collection.controlPoint(point));
                      collection.controlPoint(point).add(collection.controlPoint(parent));
                    }
                  });
            }
            yield collection -> positions.forEach(position -> position.accept(collection));
          }
          default -> null;
        };
    if (control != null) {
      controlOperators.add(control);
      return (collection, offset) -> control.accept(collection);
    }
    ParticleOperator operation = compileParticleOperator(operator);
    return (collection, offset) -> {
      for (Particle particle : collection.particles) operation.accept(collection, particle, offset);
    };
  }

  private BiConsumer<Collection, Particle> compileInitializer(JsonObject operator) {
    return switch (name(operator)) {
      case "Radius Random" -> {
        float low = scalar(operator, "radius_min"), high = scalar(operator, "radius_max");
        float exponent = scalar(operator, "radius_random_exponent");
        yield (collection, particle) ->
            particle.radius = low + (high - low) * (float) Math.pow(collection.random(), exponent);
      }
      case "Normal Modify Offset Random" -> {
        Vector3f low = vector(operator, "offset min"), high = vector(operator, "offset max");
        boolean normalize = scalar(operator, "normalize output 0/1") != 0;
        yield (collection, particle) -> {
          particle.normal.add(collection.rangeVector(low, high));
          if (normalize && particle.normal.lengthSquared() > 0) particle.normal.normalize();
        };
      }
      case "remap initial scalar" -> {
        int input = (int) scalar(operator, "input field"), output = (int) scalar(operator, "output field");
        float low = scalar(operator, "input minimum"), high = scalar(operator, "input maximum");
        float outLow = scalar(operator, "output minimum"), outHigh = scalar(operator, "output maximum");
        boolean multiply = scalar(operator, "output is scalar of initial random range") != 0;
        boolean restricted = scalar(operator, "only active within specified input range") != 0;
        yield (collection, particle) -> {
          float value = particle.scalar(input);
          if (!restricted || value >= low && value <= high)
            particle.scalar(output, remap(value, low, high, outLow, outHigh), multiply);
        };
      }
      case "Position Along Ring" -> {
        float evenDistributionCount = scalar(operator, "even distribution count");
        float initialRadius = scalar(operator, "initial radius");
        float yaw = scalar(operator, "yaw");
        float pitch = scalar(operator, "pitch");
        float roll = scalar(operator, "roll");
        float minInitialSpeed = scalar(operator, "min initial speed");
        float maxInitialSpeed = scalar(operator, "max initial speed");
        float xyVelocityOnly = scalar(operator, "XY velocity only");
        Quaternionf rotation =
            new Quaternionf()
                .rotationZYX(
                    (float) Math.toRadians(yaw),
                    (float) Math.toRadians(pitch),
                    (float) Math.toRadians(roll));
        yield (collection, particle) -> {
          collection.randomIndex += 3;
          float theta = (particle.id + 1) * (float) (Math.PI * 2) / evenDistributionCount;
          float radius = initialRadius;
          particle.position.set((float) Math.cos(theta) * radius, (float) Math.sin(theta) * radius, 0);
          rotation.transform(particle.position);
          Vector3f velocity =
              new Vector3f(particle.position)
                  .normalize()
                  .mul(collection.range(minInitialSpeed, maxInitialSpeed));
          if (xyVelocityOnly != 0) velocity.z = 0;
          particle.previous.set(particle.position).sub(velocity.mul(STEP));
        };
      }
      case "Position Within Sphere Random" -> {
        float distanceMin = scalar(operator, "distance_min");
        float distanceMax = scalar(operator, "distance_max");
        Vector3f distanceBias = vector(operator, "distance_bias");
        Vector3f speedInLocalCoordinateSystemMin =
            vector(operator, "speed_in_local_coordinate_system_min");
        Vector3f speedInLocalCoordinateSystemMax =
            vector(operator, "speed_in_local_coordinate_system_max");
        yield (collection, particle) -> {
          float cosine = 1 - 2 * (collection.random() * .9999F + .0001F);
          double angle = (collection.random() * .9999F + .0001F) * Math.PI * 2;
          float radius =
              distanceMin
                  + (distanceMax - distanceMin)
                      * (float) Math.pow(collection.random() * .9999F + .0001F, .3333333432674408);
          float sine = (float) Math.sqrt(1 - cosine * cosine);
          particle.position
              .set((float) Math.cos(angle) * sine, (float) Math.sin(angle) * sine, cosine)
              .mul(radius)
              .mul(distanceBias);
          particle.previous
              .set(particle.position)
              .sub(
                  collection.rangeVector(speedInLocalCoordinateSystemMin, speedInLocalCoordinateSystemMax)
                      .mul(STEP));
        };
      }
      case "Position Modify Warp Random" -> {
        Vector3f warpMin = vector(operator, "warp min");
        Vector3f warpMax = vector(operator, "warp max");
        yield (collection, particle) -> {
          Vector3f scale = collection.rangeVector(warpMin, warpMax);
          particle.position.mul(scale);
          particle.previous.mul(scale);
        };
      }
      case "Position Modify Offset Random" -> {
        Vector3f offsetMin = vector(operator, "offset min");
        Vector3f offsetMax = vector(operator, "offset max");
        yield (collection, particle) -> {
          Vector3f offset = collection.rangeVector(offsetMin, offsetMax);
          particle.position.add(offset);
          particle.previous.add(offset);
        };
      }
      case "Velocity Random" -> {
        Vector3f speedInLocalCoordinateSystemMin =
            vector(operator, "speed_in_local_coordinate_system_min");
        Vector3f speedInLocalCoordinateSystemMax =
            vector(operator, "speed_in_local_coordinate_system_max");
        yield (collection, particle) -> {
          particle.previous.sub(
              collection.rangeVector(speedInLocalCoordinateSystemMin, speedInLocalCoordinateSystemMax)
                  .mul(STEP));
        };
      }
      case "Velocity Repulse from World" -> {
        float offset = vector(operator, "maximum velocity").z;
        yield (collection, particle) -> {
          particle.position.z += offset;
          particle.previous.z += offset;
        };
      }
      case "Lifetime Random" -> {
        float lifetimeMin = scalar(operator, "lifetime_min");
        float lifetimeMax = scalar(operator, "lifetime_max");
        yield (collection, particle) -> {
          particle.life = collection.range(lifetimeMin, lifetimeMax);
        };
      }
      case "Alpha Random" -> {
        float alphaMin = scalar(operator, "alpha_min");
        float alphaMax = scalar(operator, "alpha_max");
        yield (collection, particle) -> {
          particle.alpha = collection.range(alphaMin, alphaMax) / 255;
        };
      }
      case "Rotation Random" -> {
        float rotationInitial = scalar(operator, "rotation_initial");
        float rotationOffsetMin = scalar(operator, "rotation_offset_min");
        float rotationOffsetMax = scalar(operator, "rotation_offset_max");
        yield (collection, particle) -> {
          particle.rotation =
              (float)
                  Math.toRadians(rotationInitial + collection.range(rotationOffsetMin, rotationOffsetMax));
        };
      }
      case "Color Random" -> {
        Vector3f color1 = vector(operator, "color1");
        Vector3f color2 = vector(operator, "color2");
        yield (collection, particle) -> {
          particle.rgb.set(collection.rangeVector(color1, color2)).div(255);
        };
      }
      case "Remap Control Point to Vector" -> {
        Vector3f inputMinimum = vector(operator, "input minimum");
        Vector3f inputMaximum = vector(operator, "input maximum");
        Vector3f outputMinimum = vector(operator, "output minimum");
        Vector3f outputMaximum = vector(operator, "output maximum");
        yield (collection, particle) -> {
          for (int axis = 0; axis < 3; axis++)
            particle.rgb.setComponent(
                axis,
                remap(
                    collection.simulation.color.get(axis),
                    inputMinimum.get(axis),
                    inputMaximum.get(axis),
                    outputMinimum.get(axis),
                    outputMaximum.get(axis)));
        };
      }
      case "Remap Noise to Scalar" -> {
        Vector3f spatialCoordinateOffset = vector(operator, "spatial coordinate offset");
        float spatialNoiseCoordinateScale = scalar(operator, "spatial noise coordinate scale");
        float timeCoordinateOffset = scalar(operator, "time coordinate offset");
        float timeNoiseCoordinateScale = scalar(operator, "time noise coordinate scale");
        float outputMaximum = scalar(operator, "output maximum");
        float outputMinimum = scalar(operator, "output minimum");
        float outputField = scalar(operator, "output field");
        yield (collection, particle) -> {
          Vector3f position =
              new Vector3f(particle.position)
                  .add(spatialCoordinateOffset)
                  .mul(spatialNoiseCoordinateScale);
          float t =
              (float)
                  ((collection.timelineOffset() + particle.born + timeCoordinateOffset)
                      * timeNoiseCoordinateScale);
          float noise = PortalNoiseField.sample(position.x + t, position.y + t, position.z + t);
          float result = (noise + 1) * .5F * (outputMaximum - outputMinimum) + outputMinimum;
          particle.scalar((int) outputField, result, false);
        };
      }
      case "Remap Particle Count to Scalar" -> {
        float onlyActiveWithinSpecifiedInputRange =
            scalar(operator, "only active within specified input range");
        float inputMinimum = scalar(operator, "input minimum");
        float inputMaximum = scalar(operator, "input maximum");
        float outputField = scalar(operator, "output field");
        float outputMinimum = scalar(operator, "output minimum");
        float outputMaximum = scalar(operator, "output maximum");
        float outputIsScalarOfInitialRandomRange =
            scalar(operator, "output is scalar of initial random range");
        yield (collection, particle) -> {
          if (onlyActiveWithinSpecifiedInputRange != 0
              && (particle.id < inputMinimum || particle.id > inputMaximum)) return;
          particle.scalar(
              (int) outputField,
              remap(particle.id, inputMinimum, inputMaximum, outputMinimum, outputMaximum),
              outputIsScalarOfInitialRandomRange != 0);
        };
      }
      case "Remap Initial Distance to Control Point to Scalar" -> {
        float outputField = scalar(operator, "output field");
        float distanceMinimum = scalar(operator, "distance minimum");
        float distanceMaximum = scalar(operator, "distance maximum");
        float outputMinimum = scalar(operator, "output minimum");
        float outputMaximum = scalar(operator, "output maximum");
        float outputIsScalarOfInitialRandomRange =
            scalar(operator, "output is scalar of initial random range");
        yield (collection, particle) -> {
          particle.scalar(
              (int) outputField,
              remap(
                  particle.position.length(),
                  distanceMinimum,
                  distanceMaximum,
                  outputMinimum,
                  outputMaximum),
              outputIsScalarOfInitialRandomRange != 0);
        };
      }
      default -> throw new IllegalStateException("Unported portal initializer: " + name(operator));
    };
  }

  private ParticleForce compileForce(JsonObject operator) {
    float start = scalar(operator, "operator start fadein"), end = scalar(operator, "operator end fadeout");
    float amount = scalar(operator, "amount of force");
    ParticleForce force =
        switch (name(operator)) {
          case "Pull towards control point" -> {
            float power = scalar(operator, "falloff power");
            yield (collection, particle, result) -> {
              float distance = particle.position.length();
              if (distance == 0) return;
              float magnitude = amount / (float) Math.pow(distance, power);
              result.add(collection.forceDirection.set(particle.position).mul(-magnitude / distance));
            };
          }
          case "twist around axis" -> {
            Vector3f axis = vector(operator, "twist axis");
            yield (collection, particle, result) -> {
              collection.forceDirection.set(axis).cross(particle.position);
              if (collection.forceDirection.lengthSquared() > 0)
                result.add(collection.forceDirection.normalize().mul(amount));
            };
          }
          default -> throw new IllegalStateException("Unported portal force: " + name(operator));
        };
    return (collection, particle, result) -> {
      if (collection.time >= start && (end <= 0 || collection.time < end))
        force.add(collection, particle, result);
    };
  }

  private Oscillation compileOscillation(JsonObject operator) {
    float multiplier = scalar(operator, "oscillation multiplier"),
        phase = scalar(operator, "oscillation start phase");
    if (name(operator).equals("Oscillate Scalar")) {
      float frequencyMin = scalar(operator, "oscillation frequency min"),
          frequencyMax = scalar(operator, "oscillation frequency max");
      float rateMin = scalar(operator, "oscillation rate min"),
          rateMax = scalar(operator, "oscillation rate max");
      int field = (int) scalar(operator, "oscillation field");
      return (collection, particle, offset, t) -> {
        float frequency = frequencyMin + (frequencyMax - frequencyMin) * collection.random(particle, offset);
        float rate = rateMin + (rateMax - rateMin) * collection.random(particle, offset + 1);
        particle.scalar(
            field,
            particle.scalar(field) + rate * STEP * sourceSine(frequency * t * multiplier + phase),
            false);
      };
    }
    Vector3f rateMin = vector(operator, "oscillation rate min"),
        rateMax = vector(operator, "oscillation rate max");
    Vector3f frequencyMin = vector(operator, "oscillation frequency min"),
        frequencyMax = vector(operator, "oscillation frequency max");
    int[] rates = {3, 7, 9}, frequencies = {8, 12, 15};
    return (collection, particle, offset, t) -> {
      for (int axis = 0; axis < 3; axis++) {
        float rate =
            rateMin.get(axis)
                + (rateMax.get(axis) - rateMin.get(axis)) * collection.random(particle, offset + rates[axis]);
        float frequency =
            frequencyMin.get(axis)
                + (frequencyMax.get(axis) - frequencyMin.get(axis))
                    * collection.random(particle, offset + frequencies[axis]);
        particle.position.setComponent(
            axis,
            particle.position.get(axis) + rate * STEP * sourceSine(frequency * t * multiplier + phase));
      }
    };
  }

  private ParticleOperator compileParticleOperator(JsonObject operator) {
    return switch (name(operator)) {
      case "Alpha Fade and Decay" -> {
        float inStart = scalar(operator, "start_fade_in_time"), inEnd = scalar(operator, "end_fade_in_time");
        float outStart = scalar(operator, "start_fade_out_time"),
            outEnd = scalar(operator, "end_fade_out_time");
        float startAlpha = scalar(operator, "start_alpha"), endAlpha = scalar(operator, "end_alpha");
        yield (collection, particle, randomOffset) -> {
          float life = (collection.time - particle.born) / particle.life;
          particle.alpha =
              particle.initialAlpha
                  * (life < inEnd
                      ? SourceParticleKinematics.fade(life, inStart, inEnd, startAlpha, 1)
                      : life >= outStart
                          ? SourceParticleKinematics.fade(life, outStart, outEnd, 1, endAlpha)
                          : 1);
          particle.dead = life >= 1;
        };
      }
      case "Cull when crossing sphere" -> {
        Vector3f offset = vector(operator, "Control Point offset");
        int point = (int) scalar(operator, "Control Point");
        float distance = scalar(operator, "Cull Distance");
        boolean inside = scalar(operator, "Cull inside instead of outside") != 0;
        yield (collection, particle, randomOffset) -> {
          var center = collection.controlPoint(point);
          boolean within =
              particle.position.distanceSquared(
                      center.x + offset.x, center.y + offset.y, center.z + offset.z)
                  < distance * distance;
          if (inside == within) particle.dead = true;
        };
      }
      case "Movement Basic" -> {
        Vector3f gravity = vector(operator, "gravity");
        float drag = scalar(operator, "drag");
        float momentum = SourceParticleKinematics.momentumScale(drag, STEP, STEP);
        yield (collection, particle, randomOffset) -> {
          collection.acceleration.set(gravity);
          for (ParticleForce force : forces) force.add(collection, particle, collection.acceleration);
          for (int axis = 0; axis < 3; axis++)
            collection.nextPosition.setComponent(
                axis,
                SourceParticleKinematics.movement(
                    particle.position.get(axis),
                    particle.previous.get(axis),
                    collection.acceleration.get(axis),
                    STEP,
                    momentum));
          particle.previous.set(particle.position);
          particle.position.set(collection.nextPosition);
          for (DistanceConstraint constraint : constraints) {
            float distance = particle.position.length();
            if (distance > 0)
              particle.position.mul(
                  Math.clamp(distance, constraint.minimum, constraint.maximum) / distance);
          }
        };
      }
      case "Movement Lock to Control Point" -> {
        // Portal and indicator lock points are stationary in their local simulation frame.
        yield (collection, particle, randomOffset) -> {};
      }
      case "Remap Direction to CP to Vector" -> {
        float controlPoint = scalar(operator, "control point");
        float normalize = scalar(operator, "normalize");
        Vector3f offsetAxis = vector(operator, "offset axis");
        float offsetRotation = scalar(operator, "offset rotation");
        float scaleFactor = scalar(operator, "scale factor");
        boolean rotate = offsetAxis.lengthSquared() > 0;
        if (rotate) offsetAxis.normalize();
        float radians = (float) Math.toRadians(offsetRotation);
        yield (collection, particle, randomOffset) -> {
          Vector3f target = collection.controlPoint((int) controlPoint);
          particle.normal.set(particle.position).sub(target);
          if (normalize != 0 && particle.normal.lengthSquared() > 0) particle.normal.normalize();
          if (rotate) particle.normal.rotateAxis(radians, offsetAxis.x, offsetAxis.y, offsetAxis.z);
          particle.normal.mul(scaleFactor);
        };
      }
      case "Oscillate Scalar", "Oscillate Vector" -> {
        float startMin = scalar(operator, "start time min"), startMax = scalar(operator, "start time max");
        float endMin = scalar(operator, "end time min"), endMax = scalar(operator, "end time max");
        boolean proportionalWindow = scalar(operator, "start/end proportional") != 0;
        boolean proportionalTime = scalar(operator, "proportional 0/1") != 0;
        Oscillation oscillation = compileOscillation(operator);
        yield (collection, particle, randomOffset) -> {
          float age = collection.time - particle.born, life = age / particle.life;
          float start = startMin + (startMax - startMin) * collection.random(particle, randomOffset + 11);
          float end = endMin + (endMax - endMin) * collection.random(particle, randomOffset + 12);
          float checkTime = proportionalWindow ? life : age;
          if (checkTime >= start && checkTime < end)
            oscillation.apply(
                collection,
                particle,
                randomOffset,
                proportionalTime ? life : (float) collection.timeline());
        };
      }
      case "Clamp Scalar" -> {
        float outputField = scalar(operator, "output field");
        float outputMinimum = scalar(operator, "output minimum");
        float outputMaximum = scalar(operator, "output maximum");
        yield (collection, particle, randomOffset) -> {
          int field = (int) outputField;
          particle.scalar(field, Math.clamp(particle.scalar(field), outputMinimum, outputMaximum), false);
        };
      }
      case "Ramp Scalar Linear Simple" -> {
        float startTime = scalar(operator, "start time");
        float endTime = scalar(operator, "end time");
        float rampField = scalar(operator, "ramp field");
        float rampRate = scalar(operator, "ramp rate");
        yield (collection, particle, randomOffset) -> {
          float age = collection.time - particle.born;
          if (age >= startTime && age <= endTime) {
            int field = (int) rampField;
            particle.scalar(field, particle.scalar(field) + rampRate * STEP, false);
          }
        };
      }
      case "Alpha Fade In Random" -> {
        float minimum = scalar(operator, "fade in time min"), maximum = scalar(operator, "fade in time max");
        float exponent = scalar(operator, "fade in time exponent");
        boolean proportional = scalar(operator, "proportional 0/1") != 0;
        yield (collection, particle, randomOffset) -> {
          float duration =
              minimum
                  + (maximum - minimum)
                      * (float) Math.pow(collection.random(particle, randomOffset), exponent);
          float age = (collection.time - particle.born) / (proportional ? particle.life : 1);
          if (age < duration)
            particle.alpha = particle.initialAlpha * SourceParticleKinematics.fade(age, 0, duration, 0, 1);
        };
      }
      case "Alpha Fade In Simple" -> {
        float proportionalFadeInTime = scalar(operator, "proportional fade in time");
        yield (collection, particle, randomOffset) -> {
          float age = collection.time - particle.born, life = age / particle.life;
          float end = proportionalFadeInTime;
          if (life < end)
            particle.alpha = particle.initialAlpha * SourceParticleKinematics.fade(life, 0, end, 0, 1);
        };
      }
      case "Alpha Fade Out Simple" -> {
        float proportionalFadeOutTime = scalar(operator, "proportional fade out time");
        yield (collection, particle, randomOffset) -> {
          float age = collection.time - particle.born, life = age / particle.life;
          float start = 1 - proportionalFadeOutTime;
          if (life >= start)
            particle.alpha =
                Math.min(
                    particle.alpha,
                    particle.initialAlpha * SourceParticleKinematics.fade(life, start, 1, 1, 0));
        };
      }
      case "Alpha Fade Out Random" -> {
        float fadeOutTimeMin = scalar(operator, "fade out time min");
        float fadeOutTimeMax = scalar(operator, "fade out time max");
        float proportional01 = scalar(operator, "proportional 0/1");
        yield (collection, particle, randomOffset) -> {
          float age = collection.time - particle.born, life = age / particle.life;
          float duration =
              fadeOutTimeMin + (fadeOutTimeMax - fadeOutTimeMin) * collection.random(particle, randomOffset);
          float limit = proportional01 != 0 ? 1 : particle.life;
          float t = proportional01 != 0 ? life : age;
          if (t >= limit - duration)
            particle.alpha =
                particle.initialAlpha * SourceParticleKinematics.fade(t, limit - duration, limit, 1, 0);
        };
      }
      case "Radius Scale" -> {
        float startTime = scalar(operator, "start_time");
        float endTime = scalar(operator, "end_time");
        float scaleBias = scalar(operator, "scale_bias");
        float radiusStartScale = scalar(operator, "radius_start_scale");
        float radiusEndScale = scalar(operator, "radius_end_scale");
        yield (collection, particle, randomOffset) -> {
          float age = collection.time - particle.born, life = age / particle.life;
          if (life < startTime || life > endTime) return;
          float t = remap(life, startTime, endTime, 0, 1);
          float bias = scaleBias;
          if (bias != .5F) t = t / ((1 / bias - 2) * (1 - t) + 1);
          particle.radius =
              particle.initialRadius * (radiusStartScale + (radiusEndScale - radiusStartScale) * t);
        };
      }
      case "Lifespan Decay" -> {
        yield (collection, particle, randomOffset) -> {
          float age = collection.time - particle.born;
          particle.dead = age >= particle.life;
        };
      }
      case "Lifespan Minimum Radius Decay" -> {
        float minimumRadius = scalar(operator, "minimum radius");
        yield (collection, particle, randomOffset) -> {
          particle.dead = particle.radius <= minimumRadius;
        };
      }
      case "Movement Dampen Relative to Control Point" -> {
        float falloffRange = scalar(operator, "falloff range");
        float dampenScale = scalar(operator, "dampen scale");
        yield (collection, particle, randomOffset) -> {
          float weight =
              (float) Math.pow(Math.min(1, particle.position.length() / falloffRange), dampenScale);
          particle.position.sub(particle.previous).mul(weight).add(particle.previous);
        };
      }
      default -> throw new IllegalStateException("Unported portal operator: " + name(operator));
    };
  }
}
