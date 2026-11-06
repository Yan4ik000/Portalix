package dev.portalix.client.particle;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.portalix.client.PortalixClient;
import dev.portalix.client.gun.animation.GunAnimator;
import dev.portalix.client.gun.model.GunAttachment;
import dev.portalix.client.gun.model.GunModel;
import dev.portalix.client.render.BillboardOrientation;
import dev.portalix.client.render.ParticleGeometry;
import dev.portalix.client.render.ParticleRenderTypes;
import dev.portalix.resource.ModAssetLoader;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class CarryParticleRenderer {
  private static final GunAttachment[] SOCKETS = {
    GunAttachment.MUZZLE, GunAttachment.ARM_1,
    GunAttachment.ARM_2, GunAttachment.ARM_3
  };
  private static ParticleMaterialLibrary definitions;
  private static ParticleRecordingPlayer firstPerson;
  private static ParticleRecordingPlayer thirdPerson;
  private static final ThreadLocal<RopeBuffer> ropeBuffer =
      ThreadLocal.withInitial(RopeBuffer::new);

  private CarryParticleRenderer() {}

  private static ParticleRecordingPlayer loadPlayback(
      String name, ParticleMaterialLibrary materials) {
    String path = "/assets/portalix/source_particles/" + name;
    try {
      JsonObject metadata = ModAssetLoader.json(path + ".playback.json");
      var recording = new ParticleRecording(ModAssetLoader.open(path + ".pgp"));
      return new ParticleRecordingPlayer(
          recording, materials.ropes(), metadata.get("loopStart").getAsFloat());
    } catch (IOException | RuntimeException cause) {
      throw new IllegalStateException("Cannot load Source particle playback " + name, cause);
    }
  }

  public static void hand(
      GunModel mesh,
      Matrix4f[] matrices,
      PoseStack pose,
      SubmitNodeCollector collector,
      float partial) {
    render(PortalixClient.localAnimation(), firstPerson, mesh, matrices, pose, collector, partial);
  }

  public static void world(
      int id, GunModel mesh, Matrix4f[] matrices, PoseStack pose, SubmitNodeCollector collector) {
    Minecraft mc = Minecraft.getInstance();
    render(
        PortalixClient.animation(id),
        thirdPerson,
        mesh,
        matrices,
        pose,
        collector,
        mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
  }

  private static void render(
      GunAnimator animation,
      ParticleRecordingPlayer clip,
      GunModel mesh,
      Matrix4f[] matrices,
      PoseStack pose,
      SubmitNodeCollector collector,
      float partial) {
    float age = animation.holdAge(partial);
    if (animation.isCarrying() || !(animation.releaseAge(partial) >= clip.tailDuration())) {
      Vector3f[] points = new Vector3f[4];

      for (int i = 0; i < 4; i++) {
        points[i] = mesh.attachment(SOCKETS[i], matrices);
      }

      BillboardOrientation facing =
          new BillboardOrientation(RenderSystem.getModelViewMatrixCopy().mul(pose.last().pose()));
      Vector3f camera = facing.camera();
      float stop = animation.isCarrying() ? Float.POSITIVE_INFINITY : animation.holdStopAge();

      for (String system : clip.systems()) {
        ParticleMaterialLibrary.Definition definition = definitions.system(system);
        ParticleMaterialLibrary.Material settings = definition.material();
        if (!definition.renderers().isEmpty()) {
          RenderType type =
              ParticleRenderTypes.source(
                  settings.texture(), settings.overbright(), settings.ignoreDepth());

          collector.submitCustomGeometry(
              pose,
              type,
              (p, b) ->
                  clip.sampleSystem(
                      age,
                      stop,
                      system,
                      particles -> {
                        for (ParticleMaterialLibrary.Renderer renderer : definition.renderers()) {
                          switch (renderer.kind()) {
                            case ROPE:
                              rope(
                                  p,
                                  b,
                                  particles,
                                  points,
                                  camera,
                                  renderer.subdivisions(),
                                  renderer.texel() * settings.height(),
                                  renderer.scroll() * age);
                              break;
                            case SPRITES:
                              for (ParticleRecording.Attributes particle : particles) {
                                sprite(p, b, particle, points, facing, settings);
                              }
                          }
                        }
                      }));
        }
      }
    }
  }

  public static void loadResources() {
    ParticleMaterialLibrary definitions = new ParticleMaterialLibrary();
    definitions.prepareRenderTypes();
    ParticleRecordingPlayer fp = loadPlayback("holding_fp", definitions);
    ParticleRecordingPlayer tp = loadPlayback("holding_tp", definitions);
    CarryParticleRenderer.definitions = definitions;
    firstPerson = fp;
    thirdPerson = tp;
  }

  private static int color(float value) {
    return Math.clamp(Math.round(value * 255.0F), 0, 255);
  }

  private static float spline(float a, float b, float c, float d, float t) {
    return 0.5F
        * (2.0F * b
            + (c - a) * t
            + (2.0F * a - 5.0F * b + 4.0F * c - d) * t * t
            + (-a + 3.0F * b - 3.0F * c + d) * t * t * t);
  }

  private static void rope(
      Pose pose,
      VertexConsumer builder,
      List<? extends ParticleRecording.Attributes> particles,
      Vector3f[] sockets,
      Vector3f camera,
      int subdivisions,
      float texel,
      float scroll) {
    if (particles.size() >= 2) {
      int count = (particles.size() - 1) * (subdivisions + 1) + 1;
      RopeBuffer scratch = ropeBuffer.get();
      scratch.ensure(particles.size(), count);

      for (int i = 0; i < particles.size(); i++) {
        particles.get(i).position(sockets, scratch.controls[i]);
      }

      int index = 0;

      for (int segment = 0; segment < particles.size() - 1; segment++) {
        ParticleRecording.Attributes b = particles.get(segment);
        ParticleRecording.Attributes c = particles.get(segment + 1);
        Vector3f aPos = scratch.controls[Math.max(0, segment - 1)];
        Vector3f bPos = scratch.controls[segment];
        Vector3f cPos = scratch.controls[segment + 1];
        Vector3f dPos = scratch.controls[Math.min(particles.size() - 1, segment + 2)];

        for (int step = 0; step <= subdivisions; step++, index++) {
          float t = (float) step / (subdivisions + 1);
          scratch.positions[index].set(
              spline(aPos.x, bPos.x, cPos.x, dPos.x, t),
              spline(aPos.y, bPos.y, cPos.y, dPos.y, t),
              spline(aPos.z, bPos.z, cPos.z, dPos.z, t));
          scratch.radii[index] = (b.radius() + (c.radius() - b.radius()) * t) * 0.5F;
          scratch.alphas[index] = b.alpha() + (c.alpha() - b.alpha()) * t;
          scratch.colors[index].set(
              b.red() + (c.red() - b.red()) * t,
              b.green() + (c.green() - b.green()) * t,
              b.blue() + (c.blue() - b.blue()) * t);
        }
      }

      ParticleRecording.Attributes last = particles.getLast();
      scratch.positions[index].set(scratch.controls[particles.size() - 1]);
      scratch.radii[index] = last.radius() * 0.5F;
      scratch.alphas[index] = last.alpha();
      scratch.colors[index].set(last.red(), last.green(), last.blue());

      for (int i = 0; i < count; i++) {
        Vector3f side = scratch.sides[i];
        Vector3f pos = scratch.positions[i];
        side.set(scratch.positions[Math.min(i + 1, count - 1)])
            .sub(scratch.positions[Math.max(0, i - 1)]);
        side.cross(camera.x - pos.x, camera.y - pos.y, camera.z - pos.z);
        if (side.lengthSquared() < 1.0E-10) {
          side.set(1.0F, 0.0F, 0.0F);
        }

        side.normalize();
      }

      float v = scroll;

      for (int i = 0; i < count - 1; i++) {
        float next =
            v + scratch.positions[i].distance(scratch.positions[i + 1]) / Math.max(texel, 1.0E-6F);
        Vector3f rgb = scratch.colors[i];
        Vector3f endRgb = scratch.colors[i + 1];
        ParticleGeometry.strip(
            pose,
            builder,
            scratch.positions[i],
            scratch.positions[i + 1],
            scratch.sides[i],
            scratch.sides[i + 1],
            scratch.radii[i],
            scratch.radii[i + 1],
            v,
            next,
            color(rgb.x),
            color(rgb.y),
            color(rgb.z),
            color(endRgb.x),
            color(endRgb.y),
            color(endRgb.z),
            color(scratch.alphas[i]),
            color(scratch.alphas[i + 1]));
        v = next;
      }
    }
  }

  private static void sprite(
      Pose pose,
      VertexConsumer builder,
      ParticleRecording.Attributes particle,
      Vector3f[] sockets,
      BillboardOrientation facing,
      ParticleMaterialLibrary.Material settings) {
    Vector3f center = particle.position(sockets);
    BillboardOrientation.Basis basis = facing.at(center);
    float sin = (float) Math.sin(particle.rotation());
    float cos = (float) Math.cos(particle.rotation());
    Vector3f right = new Vector3f(basis.right()).mul(cos).fma(sin, basis.up());
    Vector3f up = new Vector3f(basis.up()).mul(cos).fma(-sin, basis.right());
    ParticleMaterialLibrary.Sheet sequence = settings.sheets().get(particle.sequence());
    ParticleMaterialLibrary.UV uv =
        sequence == null ? ParticleMaterialLibrary.UV.FULL : sequence.sample(particle.age());

    for (int corner = 0; corner < 4; corner++) {
      boolean left = corner == 0 || corner == 3;
      boolean bottom = corner < 2;
      float horizontal = (left ? -1 : 1) * particle.radius();
      float vertical = (bottom ? -1 : 1) * particle.radius();
      float x = Math.fma(vertical, up.x, Math.fma(horizontal, right.x, center.x));
      float y = Math.fma(vertical, up.y, Math.fma(horizontal, right.y, center.y));
      float z = Math.fma(vertical, up.z, Math.fma(horizontal, right.z, center.z));
      builder
          .addVertex(pose.pose(), x, y, z)
          .setColor(
              color(particle.red()),
              color(particle.green()),
              color(particle.blue()),
              color(particle.alpha()))
          .setUv(left ? uv.u0() : uv.u1(), bottom ? uv.v1() : uv.v0())
          .setOverlay(0)
          .setLight(15728880)
          .setNormal(0.0F, 1.0F, 0.0F);
    }
  }

  private static final class RopeBuffer {
    Vector3f[] controls = new Vector3f[0];
    Vector3f[] positions = new Vector3f[0];
    Vector3f[] sides = new Vector3f[0];
    Vector3f[] colors = new Vector3f[0];
    float[] radii = new float[0];
    float[] alphas = new float[0];

    void ensure(int particles, int points) {
      if (this.controls.length < particles) {
        this.controls = vectors(particles);
      }

      if (this.positions.length < points) {
        this.positions = vectors(points);
        this.sides = vectors(points);
        this.colors = vectors(points);
        this.radii = new float[points];
        this.alphas = new float[points];
      }
    }

    private static Vector3f[] vectors(int size) {
      Vector3f[] values = new Vector3f[size];

      for (int i = 0; i < size; i++) {
        values[i] = new Vector3f();
      }

      return values;
    }
  }
}
