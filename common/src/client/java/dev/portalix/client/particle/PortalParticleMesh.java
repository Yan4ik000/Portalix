package dev.portalix.client.particle;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.client.particle.SourceParticleProgram.Renderer;
import dev.portalix.client.portal.render.PortalSurfaceMesh;
import dev.portalix.resource.ModAssetLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class PortalParticleMesh {
  private static final Map<String, Material> MATERIALS = materials();

  private record Material(
      String texture, int width, int orientation, boolean additive, boolean refract) {}

  private static Map<String, Material> materials() {
    var definitions = ModAssetLoader.json("/assets/portalix/portals/materials.json");
    Map<String, Material> materials = new HashMap<>();
    definitions
        .asMap()
        .forEach(
            (name, value) -> {
              var material = value.getAsJsonObject();
              materials.put(
                  name,
                  new Material(
                      material.get("texture").getAsString(),
                      material.get("textureWidth").getAsInt(),
                      material.get("orientation").getAsInt(),
                      material.get("additive").getAsBoolean(),
                      material.get("refract").getAsBoolean()));
            });
    return Map.copyOf(materials);
  }

  public record Vertex(float x, float y, float z, float u, float v, int color) {}

  public record Batch(
      String texture,
      boolean refract,
      boolean additive,
      List<Vertex> vertices,
      Object persistentKey) {}

  private record Cached(long revision, Vector3f right, Vector3f up, int stride, Batch batch) {}

  private final Map<SourceParticleSystem.Collection, Cached> cache = new IdentityHashMap<>();

  public List<Batch> build(
      PortalParticleEffect effect,
      PortalEndpoint endpoint,
      Vec3 cameraRight,
      Vec3 cameraUp,
      int stride) {
    List<Batch> result = new ArrayList<>();
    var collections = effect.collections();
    cache.keySet().retainAll(collections);
    Vector3f right = local(cameraRight, endpoint), up = local(cameraUp, endpoint);
    for (var collection : collections) {
      if (collection.particles.isEmpty()) continue;
      Renderer renderer = collection.program.renderer;
      boolean rope = renderer instanceof Renderer.Rope;
      Cached cached = cache.get(collection);
      if (cached != null
          && cached.revision == collection.simulation.revision()
          && (rope
              || cached.stride == stride && cached.right.equals(right) && cached.up.equals(up))) {
        if (cached.batch != null) result.add(cached.batch);
        continue;
      }
      List<Vertex> vertices = new ArrayList<>();
      String materialName = collection.program.material;
      Material material = MATERIALS.get(materialName);
      if (material == null)
        throw new IllegalStateException("Unported portal material: " + materialName);
      switch (renderer) {
        case Renderer.Rope parameters -> rope(collection, parameters, material, vertices);
        case Renderer.Sprites ignored -> {
          for (int index = 0; index < collection.particles.size(); index += stride) {
            var p = collection.particles.get(index);
            Vector3f x =
                new Vector3f(right)
                    .mul((float) Math.cos(p.rotation))
                    .add(new Vector3f(up).mul((float) Math.sin(p.rotation)));
            Vector3f y =
                new Vector3f(up)
                    .mul((float) Math.cos(p.rotation))
                    .sub(new Vector3f(right).mul((float) Math.sin(p.rotation)));
            quad(
                vertices,
                new Vector3f(p.position).sub(new Vector3f(y).mul(p.radius)),
                new Vector3f(p.position).add(new Vector3f(y).mul(p.radius)),
                x,
                x,
                p.radius,
                p.radius,
                0,
                1,
                p,
                p);
          }
        }
        case Renderer.Trail parameters -> {
          for (int index = 0; index < collection.particles.size(); index += stride) {
            var p = collection.particles.get(index);
            Vector3f velocity = new Vector3f(p.position).sub(p.previous).mul(60);
            float speed = velocity.length();
            if (speed == 0) continue;
            float length =
                Math.min(parameters.maxLength(), speed)
                    * Math.clamp(
                        (collection.time - p.born)
                            / parameters.fadeInTime(),
                        0,
                        1);
            Vector3f side = new Vector3f(velocity).cross(new Vector3f(right).cross(up));
            if (side.lengthSquared() == 0) continue;
            side.normalize();
            quad(
                vertices,
                new Vector3f(p.position).sub(velocity.mul(length / speed)),
                p.position,
                side,
                side,
                p.radius,
                p.radius,
                0,
                1,
                p,
                p);
          }
        }
      }
      Batch batch =
          vertices.isEmpty()
              ? null
              : new Batch(
                  material.texture,
                  material.refract,
                  material.additive,
                  List.copyOf(vertices),
                  rope ? collection : null);
      cache.put(collection, new Cached(collection.simulation.revision(), right, up, stride, batch));
      if (batch != null) result.add(batch);
    }
    return List.copyOf(result);
  }

  private static Vector3f local(Vec3 vector, PortalEndpoint portal) {

    return new Vector3f(
        (float) vector.dot(portal.right()),
        (float) vector.dot(portal.up()),
        (float) vector.dot(portal.normal()));
  }

  private record Sample(Vector3f point, Vector3f side, float radius, float alpha, Vector3f color) {}

  private static void rope(
      SourceParticleSystem.Collection collection,
      Renderer.Rope parameters,
      Material material,
      List<Vertex> vertices) {
    if (material.orientation != 3)
      throw new IllegalStateException(
          "Unsupported portal spline orientation " + material.orientation);
    var particles = collection.particles;
    if (particles.size() < 2) return;
    int subdivisions = parameters.subdivisions();
    float offset = parameters.textureOffset();
    float scroll =
        (float)
            ((collection.timeline() * parameters.scrollRate() + offset + offset / material.width)
                % 1);
    for (int i = 0; i + 1 < particles.size(); i++) {
      var b = control(particles.get(i));
      var c = control(particles.get(i + 1));
      var a = i > 0 ? control(particles.get(i - 1)) : extrapolate(b, c);
      var d = i + 2 < particles.size() ? control(particles.get(i + 2)) : extrapolate(c, b);
      float advance =
          b.position().distance(c.position()) * parameters.texelSize() / subdivisions;
      var left = SourcePortalRibbon.evaluate(a, b, c, d, 0, 0, scroll);
      var right = SourcePortalRibbon.evaluate(a, b, c, d, 0, 1, scroll);
      for (int step = 1; step <= subdivisions; step++) {
        scroll += advance;
        float t = (float) step / subdivisions;
        var nextLeft = SourcePortalRibbon.evaluate(a, b, c, d, t, 0, scroll);
        var nextRight = SourcePortalRibbon.evaluate(a, b, c, d, t, 1, scroll);
        for (var vertex : List.of(left, right, nextRight, nextLeft)) {
          Vector3f point = vertex.position();
          vertices.add(
              new Vertex(
                  point.x * PortalSurfaceMesh.SOURCE_X,
                  point.y * PortalSurfaceMesh.SOURCE_Y,
                  point.z * PortalSurfaceMesh.SOURCE_X,
                  vertex.u(),
                  vertex.v(),
                  color(vertex.color(), vertex.alpha())));
        }
        left = nextLeft;
        right = nextRight;
      }
    }
  }

  private static SourcePortalRibbon.Control control(SourceParticleSystem.Particle particle) {
    return new SourcePortalRibbon.Control(
        particle.position,
        particle.radius,
        particle.normal,
        particle.rgb,
        particle.alpha * particle.alpha2);
  }

  private static SourcePortalRibbon.Control extrapolate(
      SourcePortalRibbon.Control b, SourcePortalRibbon.Control c) {
    return new SourcePortalRibbon.Control(
        new Vector3f(b.position()).mul(2).sub(c.position()),
        2 * b.radius() - c.radius(),
        b.normal(),
        b.color(),
        b.alpha());
  }

  private static float packed(float value) {
    return SourceParticleColor.packedColor(Math.clamp(value, 0, 1));
  }

  private static Vector3f linear(Vector3f color) {
    return new Vector3f(
        SourceParticleColor.linearColor(color.x),
        SourceParticleColor.linearColor(color.y),
        SourceParticleColor.linearColor(color.z));
  }

  private static int color(Vector3f rgb, float alpha) {
    return Math.clamp(Math.round(alpha * 255), 0, 255) << 24
        | Math.clamp(Math.round(rgb.x * 255), 0, 255) << 16
        | Math.clamp(Math.round(rgb.y * 255), 0, 255) << 8
        | Math.clamp(Math.round(rgb.z * 255), 0, 255);
  }

  private static void quad(
      List<Vertex> out,
      Vector3f a,
      Vector3f b,
      Vector3f sideA,
      Vector3f sideB,
      float radiusA,
      float radiusB,
      float u,
      float next,
      SourceParticleSystem.Particle pa,
      SourceParticleSystem.Particle pb) {
    quad(
        out,
        new Sample(a, sideA, radiusA, packed(pa.alpha * pa.alpha2), linear(pa.rgb)),
        new Sample(b, sideB, radiusB, packed(pb.alpha * pb.alpha2), linear(pb.rgb)),
        u,
        next);
  }

  private static void quad(List<Vertex> out, Sample a, Sample b, float uv, float next) {
    vertex(out, a, -1, uv, 0);
    vertex(out, a, 1, uv, 1);
    vertex(out, b, 1, next, 1);
    vertex(out, b, -1, next, 0);
  }

  private static void vertex(List<Vertex> out, Sample sample, float side, float u, float v) {
    Vector3f point =
        new Vector3f(sample.point).add(new Vector3f(sample.side).mul(side * sample.radius));
    out.add(
        new Vertex(
            point.x * PortalSurfaceMesh.SOURCE_X,
            point.y * PortalSurfaceMesh.SOURCE_Y,
            point.z * PortalSurfaceMesh.SOURCE_X,
            u,
            v,
            color(sample.color, sample.alpha)));
  }
}
