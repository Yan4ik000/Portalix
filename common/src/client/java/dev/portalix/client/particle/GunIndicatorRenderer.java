package dev.portalix.client.particle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.portalix.client.gun.model.GunAttachment;
import dev.portalix.client.gun.model.GunModel;
import dev.portalix.client.particle.SourceParticleProgram.Renderer;
import dev.portalix.client.render.BillboardOrientation;
import dev.portalix.client.render.ParticleGeometry;
import dev.portalix.client.render.ParticleRenderTypes;
import dev.portalix.item.PortalixVariant;
import dev.portalix.resource.ModAssetLoader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class GunIndicatorRenderer {
  private static final List<String> ROOTS =
      List.of("portalix_top_light_firstperson", "portalix_top_light_thirdperson");
  private static SourceParticleSystem.Library systems;
  private static Map<String, Binding> bindings;
  private static final Playback[] playback = new Playback[PortalixVariant.values().length * 4];
  private static final int PARTICLE_SIZE = 12;

  private GunIndicatorRenderer() {}

  public static void loadResources() {
    systems =
        new SourceParticleSystem.Library(
            ModAssetLoader.json("/assets/portalix/source_particles/indicator.json"));
    var definitions = new ParticleMaterialLibrary();
    definitions.prepareRenderTypes();
    var compiled = new HashMap<String, Binding>();
    systems.definitions.forEach(
        (name, definition) -> {
          var program = definition.program();
          if (program == null) return;
          var material = definitions.system(name).material();
          if (program.renderer instanceof Renderer.Trail)
            throw new IllegalArgumentException("Unsupported gun indicator trail: " + name);
          compiled.put(
              name,
              new Binding(
                  ParticleRenderTypes.spritecard(material.texture(), material.overbright(), false),
                  name.equals("portalix_core_glow")
                      ? GunAttachment.BEAM_POINT_1
                      : GunAttachment.BODY_LIGHT,
                  program.renderer,
                  program.renderer instanceof Renderer.Rope rope
                      ? rope.textureOffset() / material.height()
                      : 0));
        });
    bindings = Map.copyOf(compiled);
    for (int index = 0; index < playback.length; index++) {
      var color = SourceParticleColor.gunPalette(PortalixVariant.values()[index / 4], index % 2 == 1);
      playback[index] =
          new Playback(
              new SourceParticleSystem(systems, List.of(ROOTS.get(index % 4 / 2)), color, index, 0),
              0);
    }
  }

  public static void submit(
      GunModel mesh,
      Matrix4f[] matrices,
      PoseStack pose,
      SubmitNodeCollector collector,
      BillboardOrientation facing,
      PortalixVariant variant,
      boolean orange,
      float age) {
    int index = variant.ordinal() * 4 + (mesh.isWorld() ? 2 : 0) + (orange ? 1 : 0);
    Playback current = playback[index];
    if (current == null || age < current.age) {
      var color = SourceParticleColor.gunPalette(variant, orange);
      current =
          new Playback(
              new SourceParticleSystem(
                  systems, List.of(ROOTS.get(mesh.isWorld() ? 1 : 0)), color, index, age),
              age);
      playback[index] = current;
    } else {
      current.simulation.advance(age - current.age);
      current.age = age;
    }
    Matrix4f bodyFrame = null, coreFrame = null;
    for (var collection : current.simulation.collections) {
      if (collection.particles.isEmpty()) continue;
      var binding = bindings.get(collection.name);
      Matrix4f frame;
      if (binding.attachment == GunAttachment.BEAM_POINT_1) {
        if (coreFrame == null) coreFrame = mesh.particleFrame(binding.attachment, matrices);
        frame = coreFrame;
      } else {
        if (bodyFrame == null) bodyFrame = mesh.particleFrame(binding.attachment, matrices);
        frame = bodyFrame;
      }
      float[] particles = snapshot(collection.particles, frame);
      collector.submitCustomGeometry(
          pose, binding.type, new Geometry(binding, particles, facing, age));
    }
  }

  private static float[] snapshot(List<SourceParticleSystem.Particle> particles, Matrix4f frame) {
    float[] data = new float[particles.size() * PARTICLE_SIZE];
    var position = new Vector3f();
    var normal = new Vector3f();
    for (int i = 0; i < particles.size(); i++) {
      var particle = particles.get(i);
      frame.transformPosition(position.set(particle.position));
      frame.transformDirection(normal.set(particle.normal)).normalize();
      int at = i * PARTICLE_SIZE;
      data[at] = position.x;
      data[at + 1] = position.y;
      data[at + 2] = position.z;
      data[at + 3] = normal.x;
      data[at + 4] = normal.y;
      data[at + 5] = normal.z;
      data[at + 6] = SourceParticleColor.linearColor(particle.rgb.x);
      data[at + 7] = SourceParticleColor.linearColor(particle.rgb.y);
      data[at + 8] = SourceParticleColor.linearColor(particle.rgb.z);
      data[at + 9] = particle.radius;
      data[at + 10] = particle.alpha * particle.alpha2;
      data[at + 11] = particle.rotation;
    }
    return data;
  }

  private static void sprites(
      PoseStack.Pose pose,
      VertexConsumer vertices,
      float[] particles,
      int orientation,
      BillboardOrientation facing) {
    var center = new Vector3f();
    var normal = new Vector3f();
    var right = new Vector3f();
    var up = new Vector3f();
    var rotatedRight = new Vector3f();
    var rotatedUp = new Vector3f();
    for (int at = 0; at < particles.length; at += PARTICLE_SIZE) {
      center.zero().add(particles[at], particles[at + 1], particles[at + 2]);
      if (orientation == 3) {
        normal.set(particles[at + 3], particles[at + 4], particles[at + 5]);
        right
            .set(normal)
            .cross(0, Math.abs(normal.y) > .99F ? 0 : 1, Math.abs(normal.y) > .99F ? 1 : 0)
            .normalize();
        up.set(right).cross(normal).normalize();
      } else {
        facing.at(center, right, up, normal);
      }
      float cosine = (float) Math.cos(particles[at + 11]);
      float sine = (float) Math.sin(particles[at + 11]);
      rotatedRight.set(right).mul(cosine).fma(sine, up);
      rotatedUp.set(up).mul(cosine).fma(-sine, right);
      ParticleGeometry.sprite(
          pose,
          vertices,
          center,
          rotatedRight,
          rotatedUp,
          particles[at + 9],
          channel(particles[at + 6]),
          channel(particles[at + 7]),
          channel(particles[at + 8]),
          channel(particles[at + 10]));
    }
  }

  private static void rope(
      PoseStack.Pose pose,
      VertexConsumer vertices,
      float[] particles,
      Binding binding,
      Renderer.Rope renderer,
      Vector3f camera,
      float age) {
    int subdivisions = renderer.subdivisions();
    float uv = age * renderer.scrollRate() + binding.textureOffset;
    var a = new Vector3f();
    var d = new Vector3f();
    var point = new Vector3f();
    var tangent = new Vector3f();
    var work = new Vector3f();
    var previousPoint = new Vector3f();
    var nextPoint = new Vector3f();
    var previousSide = new Vector3f();
    var nextSide = new Vector3f();
    float[] previous = new float[11], next = new float[11];
    for (int b = 0; b + PARTICLE_SIZE < particles.length; b += PARTICLE_SIZE) {
      int c = b + PARTICLE_SIZE;
      if (b > 0)
        a.set(
            particles[b - PARTICLE_SIZE],
            particles[b - PARTICLE_SIZE + 1],
            particles[b - PARTICLE_SIZE + 2]);
      else
        a.set(particles[b], particles[b + 1], particles[b + 2])
            .mul(2)
            .sub(particles[c], particles[c + 1], particles[c + 2]);
      if (c + PARTICLE_SIZE < particles.length)
        d.set(
            particles[c + PARTICLE_SIZE],
            particles[c + PARTICLE_SIZE + 1],
            particles[c + PARTICLE_SIZE + 2]);
      else
        d.set(particles[c], particles[c + 1], particles[c + 2])
            .mul(2)
            .sub(particles[b], particles[b + 1], particles[b + 2]);
      float ra = b > 0 ? particles[b - PARTICLE_SIZE + 9] : 2 * particles[b + 9] - particles[c + 9];
      float rd =
          c + PARTICLE_SIZE < particles.length
              ? particles[c + PARTICLE_SIZE + 9]
              : 2 * particles[c + 9] - particles[b + 9];
      sample(particles, b, c, a, d, ra, rd, 0, camera, point, tangent, work, previous);
      float advance =
          point
                  .set(particles[b], particles[b + 1], particles[b + 2])
                  .distance(particles[c], particles[c + 1], particles[c + 2])
              * renderer.texelSize()
              / subdivisions;
      for (int step = 1; step <= subdivisions; step++) {
        sample(
            particles,
            b,
            c,
            a,
            d,
            ra,
            rd,
            (float) step / subdivisions,
            camera,
            point,
            tangent,
            work,
            next);
        previousPoint.set(previous[0], previous[1], previous[2]);
        nextPoint.set(next[0], next[1], next[2]);
        previousSide.set(previous[3], previous[4], previous[5]);
        nextSide.set(next[3], next[4], next[5]);
        ParticleGeometry.strip(
            pose,
            vertices,
            previousPoint,
            nextPoint,
            previousSide,
            nextSide,
            previous[6],
            next[6],
            uv,
            uv + advance,
            channel(previous[8]),
            channel(previous[9]),
            channel(previous[10]),
            channel(next[8]),
            channel(next[9]),
            channel(next[10]),
            channel(previous[7]),
            channel(next[7]));
        float[] swap = previous;
        previous = next;
        next = swap;
        uv += advance;
      }
    }
  }

  private static void sample(
      float[] particles,
      int b,
      int c,
      Vector3f a,
      Vector3f d,
      float ra,
      float rd,
      float t,
      Vector3f camera,
      Vector3f point,
      Vector3f tangent,
      Vector3f work,
      float[] result) {
    for (int axis = 0; axis < 3; axis++) {
      point.setComponent(
          axis,
          ParticleSpline.position(
              a.get(axis), particles[b + axis], particles[c + axis], d.get(axis), t));
      tangent.setComponent(
          axis,
          ParticleSpline.tangent(
              a.get(axis), particles[b + axis], particles[c + axis], d.get(axis), t));
    }
    // Preserve addition of the original zero anchor, including signed-zero coordinates.
    point.add(0, 0, 0);
    tangent.cross(work.set(camera).sub(point));
    if (tangent.lengthSquared() > 0) tangent.normalize();
    result[0] = point.x;
    result[1] = point.y;
    result[2] = point.z;
    result[3] = tangent.x;
    result[4] = tangent.y;
    result[5] = tangent.z;
    result[6] =
        Math.max(0, ParticleSpline.position(ra, particles[b + 9], particles[c + 9], rd, t)) * .5F;
    result[7] = particles[b + 10] + (particles[c + 10] - particles[b + 10]) * t;
    work.set(particles[b + 6], particles[b + 7], particles[b + 8])
        .lerp(point.set(particles[c + 6], particles[c + 7], particles[c + 8]), t);
    result[8] = work.x;
    result[9] = work.y;
    result[10] = work.z;
  }

  private static int channel(float value) {
    return Math.clamp(Math.round(value * 255), 0, 255);
  }

  private record Binding(
      RenderType type,
      GunAttachment attachment,
      Renderer renderer,
      float textureOffset) {}

  private static final class Geometry implements SubmitNodeCollector.CustomGeometryRenderer {
    private final Binding binding;
    private final float[] particles;
    private final BillboardOrientation facing;
    private final float age;

    Geometry(Binding binding, float[] particles, BillboardOrientation facing, float age) {
      this.binding = binding;
      this.particles = particles;
      this.facing = facing;
      this.age = age;
    }

    @Override
    public void render(PoseStack.Pose pose, VertexConsumer vertices) {
      switch (binding.renderer) {
        case Renderer.Rope parameters ->
            rope(pose, vertices, particles, binding, parameters, facing.camera(), age);
        case Renderer.Sprites parameters ->
            sprites(pose, vertices, particles, parameters.orientation(), facing);
        case Renderer.Trail ignored ->
            throw new IllegalStateException("Unsupported gun indicator trail");
      }
    }
  }

  private static final class Playback {
    final SourceParticleSystem simulation;
    float age;

    Playback(SourceParticleSystem simulation, float age) {
      this.simulation = simulation;
      this.age = age;
    }
  }
}
