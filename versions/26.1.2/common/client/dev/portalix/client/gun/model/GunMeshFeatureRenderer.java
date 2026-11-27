package dev.portalix.client.gun.model;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.portalix.PortalixMod;
import dev.portalix.client.compat.iris.IrisDrawContext;
import dev.portalix.client.compat.iris.IrisEffectTarget;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.compat.minecraft.GunVertexAbi;
import dev.portalix.client.compat.minecraft.PipelineVariant;
import dev.portalix.client.platform.ClientPlatform.Phase;
import dev.portalix.client.portal.clip.PortalClipping;
import dev.portalix.client.portal.entity.PortalEntityClipMask;
import dev.portalix.client.portal.entity.PortalEntityClipping;
import dev.portalix.client.render.PortalDraw;
import dev.portalix.client.render.PortalRenderQueue;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

public final class GunMeshFeatureRenderer {
  private static final PortalRenderQueue.Feature<Submit> FEATURE =
      new PortalRenderQueue.Feature<>(GunMeshFeatureRenderer::draw, submit -> submit.lease.close());

  private GunMeshFeatureRenderer() {}

  private static final Map<RenderPipeline, RenderPipeline> PIPELINES = new HashMap<>();
  static RenderPipeline pipeline(RenderPipeline source) {
    return PIPELINES.computeIfAbsent(
        source,
        key -> {
          IrisShaders.register(key);
          var location =
              PortalixMod.id(
                  "pipeline/mesh_"
                      + key.getLocation().getNamespace()
                      + "_"
                      + key.getLocation().getPath());
          return RenderPipelines.register(
              new PipelineVariant(key, location)
                  .vertexShader(PortalixMod.id("core/gun_mesh"))
                  .geometry(GunVertexAbi.FORMAT, VertexFormat.Mode.QUADS)
                  .build());
        });
  }

  public record Submit(
      Matrix4f pose,
      RenderType pass,
      Geometry geometry,
      Supplier<Vertices> vertices,
      int light,
      GunModel.PoseLease lease,
      IrisDrawContext shaders,
      SurfaceLight surfaceLight,
      PortalEntityClipMask clip)
      implements PortalRenderQueue.Submission {
    public Submit(Matrix4f pose, RenderType pass, Geometry geometry, Supplier<Vertices> vertices,
        int light, GunModel.PoseLease lease, IrisDrawContext shaders, SurfaceLight surfaceLight) {
      this(pose, pass, geometry, vertices, light, lease, shaders, surfaceLight, PortalEntityClipping.submitting());
    }

    @Override
    public void enqueue(PortalRenderQueue queue, Phase phase) {
      queue.add(phase, FEATURE, this);
    }
  }

  record SurfaceLight(Vector3f origin, int color, RenderType pass) {}

  private static void draw(List<Submit> submits) {
    boolean shaders = IrisShaders.active();
    for (var submit : submits) PortalClipping.withEntity(submit.clip, () -> draw(submit, shaders));
  }

  @SuppressWarnings("try")
  private static void draw(Submit submit, boolean shaders) {
    var renderType = submit.pass;
    var modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
    if (shaders) modelView.mul(submit.pose);
    var textureMatrix = shaders ? new Matrix4f() : submit.pose;
    var transforms = RenderSystem.getDynamicUniforms().writeTransform(
        modelView, new Vector4f(1),
        shaders ? new Vector3f() : new Vector3f(submit.light & 65535, submit.light >>> 16, 0),
        textureMatrix);
    var draw = new PortalDraw(shaders ? renderType.pipeline() : pipeline(renderType.pipeline()),
        renderType.outputTarget(), transforms, PortalDraw.scissor(), PortalDraw.textures(renderType));
    try (var ignored = submit.shaders == null ? null : submit.shaders.begin()) {
      var vertices = submit.vertices.get();
      submit.geometry.draw(vertices, draw,
          shaders ? renderType.format() : GunVertexAbi.FORMAT, shaders ? submit.light : 0);
      drawSurfaceLight(submit.geometry, vertices, submit.surfaceLight, modelView, textureMatrix);
    }
  }

  @SuppressWarnings("try")
  private static void drawSurfaceLight(Geometry geometry, Vertices vertices, SurfaceLight light,
      Matrix4f modelView, Matrix4f textureMatrix) {
    if (light == null || IrisShaders.shadowPass() || !IrisEffectTarget.supportsColorEffects()) return;

    int rgb = light.color;
    var tint = new Vector4f((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, 1);
    var transforms = RenderSystem.getDynamicUniforms().writeTransform(modelView, tint, light.origin, textureMatrix);
    var pass = new PortalDraw(light.pass.pipeline(), light.pass.outputTarget(), transforms,
        PortalDraw.scissor(), PortalDraw.textures(light.pass));
    try (var ignored = IrisEffectTarget.begin()) {
      geometry.draw(vertices, pass, GunVertexAbi.FORMAT, 0);
    }
  }

  static final class Vertices {
    final float[] values;
    long revision;

    Vertices(float[] values) {
      this.values = values;
    }
  }

  static final class Geometry implements AutoCloseable {
    private final boolean mirrored;
    private final Map<VertexFormat, Uploaded> uploads = new IdentityHashMap<>();

    Geometry(boolean mirrored) {
      this.mirrored = mirrored;
    }

    void draw(Vertices vertices, PortalDraw draw, VertexFormat format, int light) {
      Uploaded geometry = upload(vertices, format, light);
      var indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
      draw.draw(new PortalDraw.Geometry(
          geometry.buffer,
          indices.getBuffer(geometry.indexCount),
          indices.type(),
          geometry.indexCount));
    }

    void upload(Vertices dataVertices) {
      upload(dataVertices, GunVertexAbi.FORMAT, 0);
    }

    private Uploaded upload(Vertices dataVertices, VertexFormat format, int light) {
      Uploaded geometry = uploads.computeIfAbsent(format, ignored -> new Uploaded());
      float[] vertices = dataVertices.values;
      long shaderContext = IrisDrawContext.vertexKey();
      if (geometry.buffer == null
          || geometry.previous != dataVertices
          || geometry.revision != dataVertices.revision) {
        try (ByteBufferBuilder storage =
            new ByteBufferBuilder(vertices.length / 24 * 4 * format.getVertexSize())) {
          BufferBuilder builder = new BufferBuilder(storage, VertexFormat.Mode.QUADS, format);
          for (int tri = 0; tri < vertices.length; tri += 24) {
            for (int corner = 0; corner < 4; corner++) {
              int vertex = mirrored ? (corner == 0 ? 0 : corner == 1 ? 2 : 1) : Math.min(corner, 2);
              int offset = tri + vertex * 8;
              builder
                  .addVertex(vertices[offset], vertices[offset + 1], vertices[offset + 2])
                  .setColor(-1)
                  .setUv(vertices[offset + 6], vertices[offset + 7])
                  .setOverlay(OverlayTexture.NO_OVERLAY)
                  .setLight(light)
                  .setNormal(vertices[offset + 3], vertices[offset + 4], vertices[offset + 5]);
            }
          }
          try (MeshData data = builder.buildOrThrow()) {
            var vertexBuffer = data.vertexBuffer();
            geometry.format = data.drawState().format();
            if (GunVertexAbi.mutableAttributes(geometry.format)) {
              if (geometry.encoded == null
                  || geometry.encoded.capacity() < vertexBuffer.remaining()) {
                var replacement =
                    MemoryUtil.memAlloc(vertexBuffer.remaining()).order(ByteOrder.nativeOrder());
                if (geometry.encoded != null) MemoryUtil.memFree(geometry.encoded);
                geometry.encoded = replacement;
              }
              geometry.encoded.clear().limit(vertexBuffer.remaining());
              geometry.encoded.put(
                  0, vertexBuffer, vertexBuffer.position(), vertexBuffer.remaining());
            }
            geometry.upload(vertexBuffer);
            geometry.indexCount = data.drawState().indexCount();
          }
        }
        geometry.previous = dataVertices;
        geometry.revision = dataVertices.revision;
        geometry.light = light;
        geometry.shaderContext = shaderContext;
      } else if (geometry.light != light || geometry.shaderContext != shaderContext) {
        if (GunVertexAbi.update(geometry.encoded, geometry.format, geometry.light, light, geometry.shaderContext, shaderContext)) geometry.upload(geometry.encoded);
        geometry.light = light;
        geometry.shaderContext = shaderContext;
      }
      return geometry;
    }

    @Override
    public void close() {
      for (Uploaded geometry : uploads.values()) {
        if (geometry.buffer != null) geometry.buffer.close();
        if (geometry.encoded != null) MemoryUtil.memFree(geometry.encoded);
      }
      uploads.clear();
    }

    private static final class Uploaded {
      GpuBuffer buffer;
      ByteBuffer encoded;
      VertexFormat format;
      Vertices previous;
      long revision;
      int light;
      long shaderContext;
      int indexCount;

      void upload(ByteBuffer vertices) {
        if (buffer == null || buffer.size() < vertices.remaining()) {
          var replacement =
              RenderSystem.getDevice()
                  .createBuffer(
                      () -> "Portalix mesh",
                      GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                      vertices);
          if (buffer != null) buffer.close();
          buffer = replacement;
        } else {
          RenderSystem.getDevice().createCommandEncoder()
              .writeToBuffer(buffer.slice(0, vertices.remaining()), vertices.duplicate());
        }
      }

    }
  }
}
