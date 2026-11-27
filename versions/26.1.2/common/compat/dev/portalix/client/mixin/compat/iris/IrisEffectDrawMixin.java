package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import dev.portalix.client.compat.iris.IrisEffectTarget;
import dev.portalix.client.compat.iris.IrisPortalComposite;
import dev.portalix.client.compat.iris.IrisShaders;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(RenderType.class)
abstract class IrisEffectDrawMixin {
  @SuppressWarnings("try")
  @WrapMethod(method = "draw")
  private void portalix$effectTarget(MeshData mesh, Operation<Void> original) {
    var pipeline = ((RenderType) (Object) this).pipeline();
    if (!pipeline.getVertexShader().getNamespace().equals("portalix")) {
      original.call(mesh);
      return;
    }
    if (IrisShaders.shadowPass()) {
      mesh.close();
      return;
    }
    if (IrisEffectTarget.supportsColorEffects()) {
      try (var ignored = IrisEffectTarget.begin()) {
        original.call(mesh);
      }
      return;
    }
    var projection = RenderSystem.getProjectionMatrixBuffer();
    var projectionType = RenderSystem.getProjectionType();
    var modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
    var scissor = portalix$scissor();
    MeshData deferred;
    try (mesh) {
      var vertices = mesh.vertexBuffer();
      var indices = mesh.indexBuffer();
      var size = vertices.remaining() + (indices == null ? 0 : indices.remaining());
      var buffers = ByteBufferBuilder.exactlySized(size);
      IrisPortalComposite.trackDeferred(buffers);
      deferred = new MeshData(portalix$copy(buffers, vertices), mesh.drawState());
      if (indices != null) ((IrisMeshDataAccessor) deferred).portalix$indexBuffer(portalix$copy(buffers, indices));
    }
    IrisEffectTarget.deferDraw(() -> {
      var savedProjection = RenderSystem.getProjectionMatrixBuffer();
      var savedType = RenderSystem.getProjectionType();
      var savedScissor = portalix$scissor();
      var stack = RenderSystem.getModelViewStack();
      stack.pushMatrix();
      try (var ignored = IrisEffectTarget.begin()) {
        stack.set(modelView);
        portalix$scissor(scissor);
        RenderSystem.setProjectionMatrix(projection, projectionType);
        original.call(deferred);
      } finally {
        RenderSystem.setProjectionMatrix(savedProjection, savedType);
        portalix$scissor(savedScissor);
        stack.popMatrix();
      }
    });
  }

  @Unique
  private static ByteBufferBuilder.Result portalix$copy(ByteBufferBuilder buffers, ByteBuffer data) {
    int size = data.remaining();
    MemoryUtil.memCopy(MemoryUtil.memAddress(data), buffers.reserve(size), size);
    return buffers.build();
  }

  @Unique
  private static Scissor portalix$scissor() {
    var state = RenderSystem.getScissorStateForRenderTypeDraws();
    return new Scissor(state.enabled(), state.x(), state.y(), state.width(), state.height());
  }

  @Unique private record Scissor(boolean enabled, int x, int y, int width, int height) {}

  @Unique
  private static void portalix$scissor(Scissor scissor) {
    if (scissor.enabled())
      RenderSystem.enableScissorForRenderTypeDraws(scissor.x(), scissor.y(), scissor.width(), scissor.height());
    else RenderSystem.disableScissorForRenderTypeDraws();
  }
}
