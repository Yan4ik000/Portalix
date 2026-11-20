package dev.portalix.client.gun.model;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import dev.portalix.PortalixMod;
import dev.portalix.client.compat.iris.IrisDrawContext;
import dev.portalix.client.compat.iris.IrisEffectTarget;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.compat.minecraft.GunVertexAbi;
import dev.portalix.client.compat.minecraft.PipelineVariant;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.oit.OitStage;
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
  private static final Map<OitPipelineSet, OitPipelineSet> OIT_PIPELINES = new HashMap<>();
  private static final Set<Geometry> GEOMETRY_WITH_SPARE_UPLOADS = new HashSet<>();
  private static long frame;

  public static void endFrame() {
    frame++;
    GEOMETRY_WITH_SPARE_UPLOADS.removeIf(Geometry::releaseIdleUploads);
  }

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
  private final List<Uploaded> preparedUploads = new ArrayList<>();

  public record Submit(
      Matrix4f pose,
      RenderType pass,
      Geometry geometry,
      Supplier<Vertices> vertices,
      int light,
      GunModel.PoseLease lease,
      IrisDrawContext shaders,
      SurfaceLight surfaceLight)
      implements SubmitNode {
    @Override
    public FeatureRendererType<Submit> featureType() {
      return TYPE;
    }
  }

  record SurfaceLight(Vector3f origin, int color, RenderType pass) {}

  @Override
  @SuppressWarnings("try")
  public void prepareGroup(FeatureFrameContext context, List<Submit> submits, boolean outline) {
    pending.addAll(submits);
    boolean shaders = IrisShaders.active();
    List<Prepared> prepared = new ArrayList<>(submits.size());
    for (Submit submit : submits) {
      PreparedRenderType base = submit.pass.prepare();
      var modelView = RenderSystem.getModelViewMatrixCopy();
      if (shaders) modelView.mul(submit.pose);
      var textureMatrix = shaders ? new Matrix4f() : submit.pose;
      var oit = base.oitPipelineSet();
      if (!shaders && oit != null) {
        oit = OIT_PIPELINES.computeIfAbsent(oit, key -> new OitPipelineSet(
            pipeline(key.depthBoundsPipeline()), pipeline(key.transmittancePipeline()),
            pipeline(key.accumulatePipeline())));
      }
      var material = new PreparedRenderType(
          base.name(), shaders ? base.pipeline() : pipeline(base.pipeline()), oit,
          RenderSystem.getDynamicUniforms().writeTransform(modelView, new Vector4f(1),
              shaders ? new Vector3f() : new Vector3f(submit.light & 65535, submit.light >>> 16, 0),
              textureMatrix),
          base.scissorState(), base.textures());
      var surface = prepareSurfaceLight(submit.surfaceLight, modelView, textureMatrix);
      try (var ignored = submit.shaders == null ? null : submit.shaders.begin()) {
        var vertices = submit.vertices.get();
        var mesh = prepareDraw(material, submit.geometry, vertices,
            shaders ? submit.pass.format() : GunVertexAbi.FORMAT, shaders ? submit.light : 0);
        Draw surfaceLight = surface == null ? null
            : prepareDraw(surface, submit.geometry, vertices, GunVertexAbi.FORMAT, 0);
        prepared.add(new Prepared(mesh, surfaceLight));
      }
    }
    groups.add(prepared);
  }

  private Draw prepareDraw(PreparedRenderType material, Geometry geometry, Vertices vertices,
                           VertexFormat format, int light) {
    var upload = geometry.prepare(vertices, format, light);
    upload.lastUsedFrame = frame;
    upload.preparedDraws++;
    preparedUploads.add(upload);
    return new Draw(material, upload);
  }

  private static PreparedRenderType prepareSurfaceLight(
      SurfaceLight light, Matrix4f modelView, Matrix4f textureMatrix) {
    if (light == null || IrisShaders.shadowPass()) return null;
    PreparedRenderType pass = light.pass.prepare();
    int rgb = light.color;
    var tint = new Vector4f((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, 1);
    return new PreparedRenderType(pass.name(), pass.pipeline(), null,
        RenderSystem.getDynamicUniforms().writeTransform(modelView, tint, light.origin, textureMatrix),
        pass.scissorState(), pass.textures());
  }

  @Override
  @SuppressWarnings("try")
  public void executeGroup(
      FeatureFrameContext context, OitStage stage, RenderPass pass, int groupIndex,
      List<Submit> submits, boolean outline) {
    List<Prepared> prepared = groups.get(groupIndex);
    for (int i = 0; i < submits.size(); i++) {
      Submit submit = submits.get(i);
      try (var ignored = submit.shaders == null ? null : submit.shaders.begin()) {
        var draw = prepared.get(i);
        if (stage == null) {
          draw.mesh.material.drawFromBuffer(draw.mesh.info(), pass);
        } else {
          draw.mesh.material.drawFromBufferOit(draw.mesh.info(), stage, pass);
        }
        if (draw.surfaceLight != null && stage == null && IrisEffectTarget.supportsColorEffects()) {
          draw.surfaceLight.material.drawFromBuffer(draw.surfaceLight.info(), pass);
        }
      }
    }
  }

  private record Prepared(Draw mesh, Draw surfaceLight) {}

  private record Draw(PreparedRenderType material, Uploaded geometry) {
    StagedVertexBuffer.ExecuteInfo info() {
      var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
      return new StagedVertexBuffer.ExecuteInfo(
          geometry.buffer, null, indices.type(), 0, 0, geometry.indexCount, PrimitiveTopology.QUADS);
    }
  }

  @Override
  public void finishPrepare(FeatureFrameContext context) {
    RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).resizeToRequestedIndexCount();
  }

  @Override
  public void finishExecute(FeatureFrameContext context) {
    clearFrame();
  }

  @Override
  public void close() {
    clearFrame();
  }

  private void clearFrame() {
    for (var upload : preparedUploads) upload.preparedDraws--;
    preparedUploads.clear();
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
    private static final int SPARE_UPLOAD_RETENTION_FRAMES = 120;
    private final boolean mirrored;
    // A prepared pass can refer to several different poses of the same mesh.
    private final Map<VertexFormat, List<Uploaded>> uploads = new IdentityHashMap<>();

    Geometry(boolean mirrored) {
      this.mirrored = mirrored;
    }

    void upload(Vertices dataVertices) {
      prepare(dataVertices, GunVertexAbi.FORMAT, 0);
    }

    private Uploaded prepare(Vertices dataVertices, VertexFormat format, int light) {
      float[] vertices = dataVertices.values;
      long shaderContext = format == GunVertexAbi.FORMAT ? 0 : IrisDrawContext.vertexKey();
      var slots = uploads.computeIfAbsent(format, ignored -> new ArrayList<>());
      Uploaded geometry = null;
      for (var slot : slots) {
        if (slot.previous == dataVertices && slot.revision == dataVertices.revision
            && slot.light == light && slot.shaderContext == shaderContext) {
          RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).requestIndexCount(slot.indexCount);
          return slot;
        }
        // A prepared world pass can still reference this upload while Iris draws shadows.
        if (geometry == null && slot.preparedDraws == 0) geometry = slot;
      }
      if (geometry == null) {
        geometry = new Uploaded();
        slots.add(geometry);
        if (slots.size() == 2) GEOMETRY_WITH_SPARE_UPLOADS.add(this);
      }
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
        if (GunVertexAbi.update(geometry.encoded, geometry.format, geometry.light, light,
            geometry.shaderContext, shaderContext)) {
          geometry.upload(geometry.encoded);
        }
        geometry.light = light;
        geometry.shaderContext = shaderContext;
      }
      RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).requestIndexCount(geometry.indexCount);
      return geometry;
    }

    @Override
    public void close() {
      GEOMETRY_WITH_SPARE_UPLOADS.remove(this);
      for (var slots : uploads.values()) slots.forEach(Uploaded::close);
      uploads.clear();
    }

    private boolean releaseIdleUploads() {
      boolean hasSpareUploads = false;
      for (var slots : uploads.values()) {
        // Keep one upload per format warm and discard extra uploads after sustained disuse.
        for (int i = slots.size() - 1; i > 0; i--) {
          var upload = slots.get(i);
          if (upload.preparedDraws == 0 && frame - upload.lastUsedFrame > SPARE_UPLOAD_RETENTION_FRAMES) {
            slots.remove(i).close();
          }
        }
        hasSpareUploads |= slots.size() > 1;
      }
      return !hasSpareUploads;
    }
  }

  private static final class Uploaded {
    int preparedDraws;
    long lastUsedFrame;
    GpuBuffer buffer;
    ByteBuffer encoded;
    VertexFormat format;
    Vertices previous;
    long revision;
    int light;
    long shaderContext;
    int indexCount;

    void close() {
      if (buffer != null) buffer.close();
      if (encoded != null) MemoryUtil.memFree(encoded);
    }

    void upload(ByteBuffer vertices) {
      if (buffer == null || buffer.size() < vertices.remaining()) {
        var replacement = RenderSystem.getDevice().createBuffer(
            () -> "Portalix mesh", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, vertices);
        if (buffer != null) buffer.close();
        buffer = replacement;
      } else {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        var staging = encoder.transientMemory()
            .uploadStaging(vertices.duplicate(), 4, GpuBuffer.USAGE_COPY_SRC);
        encoder.copyToBuffer(staging, buffer.slice(0, vertices.remaining()));
      }
    }
  }
}
