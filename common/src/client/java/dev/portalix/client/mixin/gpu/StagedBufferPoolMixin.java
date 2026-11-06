package dev.portalix.client.mixin.gpu;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.StagedVertexBuffer$GpuBufferPool")
abstract class StagedBufferPoolMixin {
  @Shadow @Final private List<GpuBuffer> available;
  @Shadow @Final private List<GpuBuffer> usedThisFrame;
  @Unique private int portalix$peakBuffers;

  @WrapOperation(
      method = "acquire",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/StagedVertexBuffer$GpuBufferPool;takeBestAvailable(II)Lcom/mojang/blaze3d/buffers/GpuBuffer;"))
  private GpuBuffer portalix$reuse(
      @Coerce Object pool, int minSize, int maxSize, Operation<GpuBuffer> original) {
    int best = -1;
    long bestSize = Long.MAX_VALUE;
    for (int i = 0; i < available.size(); i++) {
      long size = available.get(i).size();
      if (size >= minSize && size < bestSize) {
        best = i;
        bestSize = size;
      }
    }
    if (best >= 0) return available.remove(best);
    if (!available.isEmpty()) available.removeLast().close();
    return null;
  }

  @Inject(method = "endFrame", at = @At("HEAD"))
  private void portalix$frameDemand(GpuDevice device, CallbackInfo ci) {
    portalix$peakBuffers = Math.max(portalix$peakBuffers, usedThisFrame.size());
  }

  @WrapOperation(
      method = "endFrame",
      at =
          @At(value = "INVOKE", target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V"))
  private void portalix$retainBuffers(
      List<GpuBuffer> buffers, Consumer<GpuBuffer> close, Operation<Void> original) {
    if (buffers != available) {
      original.call(buffers, close);
      return;
    }
    if (available.size() <= portalix$peakBuffers) return;
    available.sort(Comparator.comparingLong(GpuBuffer::size).reversed());
    while (available.size() > portalix$peakBuffers) available.removeLast().close();
  }

  @WrapOperation(
      method = "endFrame",
      at = @At(value = "INVOKE", target = "Ljava/util/List;clear()V"))
  private void portalix$keepAvailable(List<?> buffers, Operation<Void> original) {
    if (buffers != available) original.call(buffers);
  }
}
