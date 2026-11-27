package dev.portalix.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.portalix.client.compat.RenderScope;
import dev.portalix.client.mixin.feature.RenderTypeStateAccessor;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.system.MemoryStack;

public record PortalDraw(RenderPipeline pipeline, OutputTarget outputTarget,
    GpuBufferSlice dynamicTransforms, ScissorState scissorState, List<Texture> textures) {
  public record Texture(String name, GpuTextureView texture, GpuSampler sampler) {}

  public record Geometry(GpuBuffer vertices, GpuBuffer indices, VertexFormat.IndexType indexType,
      int indexCount) {
    public void draw(RenderPass pass) {
      pass.setVertexBuffer(0, vertices);
      pass.setIndexBuffer(indices, indexType);
      pass.drawIndexed(0, 0, indexCount, 1);
    }
  }

  public static List<Texture> textures(RenderType source) {
    return ((RenderTypeStateAccessor) source).portalix$state().getTextures().entrySet().stream()
        .map(entry -> new Texture(entry.getKey(), entry.getValue().textureView(), entry.getValue().sampler()))
        .toList();
  }

  public static ScissorState scissor() {
    var current = RenderSystem.getScissorStateForRenderTypeDraws();
    var snapshot = new ScissorState();
    if (current.enabled()) snapshot.enable(current.x(), current.y(), current.width(), current.height());
    return snapshot;
  }

  public static RenderScope preserveScissor() {
    if (!GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST)) return GlStateManager::_disableScissorTest;
    try (var stack = MemoryStack.stackPush()) {
      var box = stack.mallocInt(4);
      GL11C.glGetIntegerv(GL11C.GL_SCISSOR_BOX, box);
      int x = box.get(0), y = box.get(1), width = box.get(2), height = box.get(3);
      return () -> {
        GlStateManager._enableScissorTest();
        GlStateManager._scissorBox(x, y, width, height);
      };
    }
  }

  public RenderPass begin(GpuTextureView color, GpuTextureView depth) {
    var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
        () -> pipeline.getLocation().toString(), color, OptionalInt.empty(), depth, OptionalDouble.empty());
    try {
      pass.setPipeline(pipeline);
      if (scissorState.enabled())
        pass.enableScissor(scissorState.x(), scissorState.y(), scissorState.width(), scissorState.height());
      RenderSystem.bindDefaultUniforms(pass);
      if (dynamicTransforms != null) pass.setUniform("DynamicTransforms", dynamicTransforms);
      for (var texture : textures) pass.bindTexture(texture.name, texture.texture, texture.sampler);
      return pass;
    } catch (RuntimeException | Error exception) {
      pass.close();
      throw exception;
    }
  }

  public RenderPass begin() {
    var target = outputTarget.getRenderTarget();
    var color = RenderSystem.outputColorTextureOverride;
    var depth = RenderSystem.outputDepthTextureOverride;
    return begin(color == null ? target.getColorTextureView() : color,
        target.useDepth ? (depth == null ? target.getDepthTextureView() : depth) : null);
  }

  public static Geometry upload(MeshData mesh) {
    var state = mesh.drawState();
    var format = state.format();
    var vertices = format.uploadImmediateVertexBuffer(mesh.vertexBuffer());
    if (mesh.indexBuffer() == null) {
      var indices = RenderSystem.getSequentialBuffer(state.mode());
      return new Geometry(vertices, indices.getBuffer(state.indexCount()), indices.type(), state.indexCount());
    } else {
      var indices = format.uploadImmediateIndexBuffer(mesh.indexBuffer());
      return new Geometry(vertices, indices, state.indexType(), state.indexCount());
    }
  }

  public void draw(Geometry geometry) {
    try (var pass = begin()) {
      geometry.draw(pass);
    }
  }
}
