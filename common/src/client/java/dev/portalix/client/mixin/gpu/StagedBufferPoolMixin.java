package dev.portalix.client.mixin.gpu;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import java.time.Duration;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.renderer.StagedVertexBuffer$GpuBufferPool")
abstract class StagedBufferPoolMixin {
  @Shadow @Final private List<GpuBuffer> available;
  @Unique private static final long portalix$RETENTION_NANOS = Duration.ofSeconds(5).toNanos();
  @Unique private final Map<GpuBuffer, BufferUse> portalix$bufferUses = new IdentityHashMap<>();

  @Shadow private void tryRecycleBuffers() { throw new AssertionError(); }

  @Inject(method = "takeBestAvailable", at = @At("HEAD"), cancellable = true)
  private void portalix$reuseBuffer(int minSize, int maxSize, CallbackInfoReturnable<GpuBuffer> cir) {
    int best = -1;
    long bestSize = Long.MAX_VALUE;
    long now = System.nanoTime();
    for (int i = 0; i < available.size(); i++) {
      var buffer = available.get(i);
      long size = buffer.size();
      if (size >= minSize && size < bestSize && (size <= maxSize
          || now - portalix$bufferUses.get(buffer).lastFitNanos < portalix$RETENTION_NANOS)) {
        best = i;
        bestSize = size;
      }
    }
    if (best < 0) {
      portalix$discardBuffers(buffer -> buffer.size() > maxSize
          && now - portalix$bufferUses.get(buffer).lastFitNanos >= portalix$RETENTION_NANOS);
      cir.setReturnValue(null);
      return;
    }
    var buffer = available.remove(best);
    var use = portalix$bufferUses.get(buffer);
    use.lastUseNanos = now;
    if (bestSize <= maxSize) use.lastFitNanos = now;
    cir.setReturnValue(buffer);
  }

  @ModifyExpressionValue(method = "acquire", at = @At(value = "INVOKE", target =
      "Lcom/mojang/blaze3d/systems/GpuDevice;createBuffer(Ljava/util/function/Supplier;IJ)Lcom/mojang/blaze3d/buffers/GpuBuffer;"))
  private GpuBuffer portalix$trackBuffer(GpuBuffer buffer) {
    portalix$bufferUses.put(buffer, new BufferUse(System.nanoTime()));
    return buffer;
  }

  @Inject(
      method = "endFrame",
      at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, ordinal = 0,
          target = "Lnet/minecraft/client/renderer/StagedVertexBuffer$GpuBufferPool;available:Ljava/util/List;"),
      cancellable = true)
  private void portalix$trimAvailableBuffers(GpuDevice device, CallbackInfo ci) {
    tryRecycleBuffers();
    long now = System.nanoTime();
    portalix$discardBuffers(buffer ->
        now - portalix$bufferUses.get(buffer).lastUseNanos >= portalix$RETENTION_NANOS);
    ci.cancel();
  }

  @Inject(method = "close", at = @At("TAIL"))
  private void portalix$closed(CallbackInfo ci) {
    portalix$bufferUses.clear();
  }

  @Unique
  private void portalix$discardBuffers(Predicate<GpuBuffer> discard) {
    available.removeIf(buffer -> {
      if (!discard.test(buffer)) return false;
      buffer.close();
      portalix$bufferUses.remove(buffer);
      return true;
    });
  }

  private static final class BufferUse {
    long lastUseNanos;
    long lastFitNanos;

    BufferUse(long now) {
      lastUseNanos = now;
      lastFitNanos = now;
    }
  }
}
