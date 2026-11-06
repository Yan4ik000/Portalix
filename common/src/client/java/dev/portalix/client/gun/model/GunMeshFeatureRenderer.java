package dev.portalix.client.gun.model;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.portalix.PortalixMod;
import dev.portalix.client.compat.iris.IrisDrawContext;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.compat.minecraft.PipelineVariant;
import dev.portalix.client.compat.minecraft.GunVertexAbi;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

public final class GunMeshFeatureRenderer implements FeatureRenderer<GunMeshFeatureRenderer.Submit> {
  public static final FeatureRendererType<Submit> TYPE =
      FeatureRendererType.create("portalix_mesh");

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
                  .geometry(GunVertexAbi.FORMAT, PrimitiveTopology.QUADS)
                  .build());
        });
  }

  private final List<List<Prepared>> groups = new ArrayList<>();
  private final List<Submit> pending = new ArrayList<>();


  public record Submit(
      Matrix4f pose,
      RenderType pass,
      Geometry geometry,
      Supplier<Vertices> vertices,
      int light,
      GunModel.PoseLease lease,
      IrisDrawContext shaders)
      implements SubmitNode {
    @Override
    public FeatureRendererType<Submit> featureType() {
      return TYPE;
    }
  }

  @Override
  public void prepareGroup(FeatureFrameContext context, List<Submit> submits, boolean outline) {
    pending.addAll(submits);
    boolean shaders = IrisShaders.active();
    List<Prepared> prepared = new ArrayList<>(submits.size());
    for (Submit submit : submits) {
      PreparedRenderType base = submit.pass.prepare();
      prepared.add(
          new Prepared(
              new PreparedRenderType(
                  shaders ? base.pipeline() : pipeline(base.pipeline()),
                  base.outputTarget(),
                  RenderSystem.getDynamicUniforms()
                      .writeTransform(
                          shaders
                              ? RenderSystem.getModelViewMatrixCopy().mul(submit.pose)
                              : RenderSystem.getModelViewMatrixCopy(),
                          new Vector4f(1),
                          shaders
                              ? new Vector3f()
                              : new Vector3f(submit.light & 65535, submit.light >>> 16, 0),
                          shaders ? new Matrix4f() : submit.pose),
                  base.scissorState(),
                  base.textures()),
              shaders ? submit.pass.format() : GunVertexAbi.FORMAT,
              shaders ? submit.light : 0));
    }
    groups.add(prepared);
  }

  @Override
  @SuppressWarnings("try")
  public void executeGroup(
      FeatureFrameContext context, int groupIndex, List<Submit> submits, boolean outline) {
    List<Prepared> prepared = groups.get(groupIndex);
    for (int i = 0; i < submits.size(); i++) {
      Submit submit = submits.get(i);
      try (var ignored = submit.shaders == null ? null : submit.shaders.begin()) {
        var renderType = prepared.get(i);
        submit.geometry.draw(submit.vertices.get(), renderType);
      } finally {
        submit.lease.close();
      }
    }
  }

  private record Prepared(PreparedRenderType renderType, VertexFormat format, int light) {}

  @Override
  public void finishExecute(FeatureFrameContext context) {
    for (Submit submit : pending) submit.lease.close();
    pending.clear();
    groups.clear();
  }

  @Override
  public void close() {
    for (Submit submit : pending) submit.lease.close();
    pending.clear();
    groups.clear();
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

    void draw(Vertices dataVertices, Prepared prepared) {
      Uploaded geometry = upload(dataVertices, prepared.format, prepared.light);
      var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
      prepared.renderType.drawFromBuffer(
          geometry.buffer,
          indices.getBuffer(geometry.indexCount),
          indices.type(),
          0,
          0,
          geometry.indexCount);
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
          BufferBuilder builder = new BufferBuilder(storage, PrimitiveTopology.QUADS, format);
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
          var encoder = RenderSystem.getDevice().createCommandEncoder();
          int size = vertices.remaining();
          var staging =
              encoder
                  .transientMemory()
                  .uploadStaging(vertices.duplicate(), 4, GpuBuffer.USAGE_COPY_SRC);
          encoder.copyToBuffer(staging, buffer.slice(0, size));
        }
      }

    }
  }
}
