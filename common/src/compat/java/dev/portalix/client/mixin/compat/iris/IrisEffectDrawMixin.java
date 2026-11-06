package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.portalix.client.compat.iris.IrisEffectTarget;
import dev.portalix.client.compat.iris.IrisShaders;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(PreparedRenderType.class)
abstract class IrisEffectDrawMixin {
  @Shadow
  public abstract RenderPipeline pipeline();

  @ModifyArg(
      method =
          "drawFromBuffer(Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/IndexType;III)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
  private RenderPipeline portalix$effectFormat(RenderPipeline pipeline) {
    var color = RenderSystem.outputColorTextureOverride;
    return color != null && pipeline.getVertexShader().getNamespace().equals("portalix")
        ? IrisEffectTarget.pipeline(pipeline, color.texture().getFormat())
        : pipeline;
  }

  @SuppressWarnings("try")
  @WrapMethod(
      method =
          "drawFromBuffer(Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/IndexType;III)V")
  private void portalix$effectTarget(
      GpuBuffer vertices,
      GpuBuffer indices,
      IndexType type,
      int baseVertex,
      int firstIndex,
      int indexCount,
      Operation<Void> original) {
    if (!pipeline().getVertexShader().getNamespace().equals("portalix")) {
      original.call(vertices, indices, type, baseVertex, firstIndex, indexCount);
      return;
    }
    if (IrisShaders.shadowPass()) return;
    try (var ignored = IrisEffectTarget.begin()) {
      original.call(vertices, indices, type, baseVertex, firstIndex, indexCount);
    }
  }
}
