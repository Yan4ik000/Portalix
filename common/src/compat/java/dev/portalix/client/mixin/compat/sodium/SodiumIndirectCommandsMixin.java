package dev.portalix.client.mixin.compat.sodium;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.caffeinemc.mods.sodium.client.gpu.device.batch.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.gpu.device.batch.VKIndirectDrawBatch;
import net.caffeinemc.mods.sodium.client.gpu.device.context.DrawContext;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkDrawIndexedIndirectCommand;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = VKIndirectDrawBatch.class, remap = false)
abstract class SodiumIndirectCommandsMixin {
  @Shadow @Final private long pCommands;
  @Unique private GpuBufferSlice portalix$commands;

  @Inject(method = "prepare", at = @At("HEAD"), cancellable = true)
  private void portalix$uploadCommands(DrawContext context, CallbackInfo ci) {
    int count = ((MultiDrawBatch) (Object) this).size;
    var data =
        MemoryUtil.memByteBuffer(
            pCommands, Math.multiplyExact(count, VkDrawIndexedIndirectCommand.SIZEOF));
    portalix$commands =
        RenderSystem.getDevice()
            .createCommandEncoder()
            .transientMemory()
            .uploadGpu(data, Integer.BYTES, GpuBuffer.USAGE_INDIRECT_PARAMETERS);
    ci.cancel();
  }

  @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
  private void portalix$drawCommands(DrawContext context, CallbackInfo ci) {
    context.getPass().drawIndexedIndirect(portalix$commands, ((MultiDrawBatch) (Object) this).size);
    ci.cancel();
  }
}
