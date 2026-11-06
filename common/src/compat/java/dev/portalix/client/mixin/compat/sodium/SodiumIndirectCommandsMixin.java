package dev.portalix.client.mixin.compat.sodium;

import com.mojang.blaze3d.buffers.GpuBuffer;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = VKIndirectDrawBatch.class, remap = false)
abstract class SodiumIndirectCommandsMixin {
  @Shadow @Final private long pCommands;

  @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
  private void portalix$uploadCommands(DrawContext context, CallbackInfo ci) {
    int count = ((MultiDrawBatch) (Object) this).size;
    var data =
        MemoryUtil.memByteBuffer(
            pCommands, Math.multiplyExact(count, VkDrawIndexedIndirectCommand.SIZEOF));
    var commands =
        RenderSystem.getDevice()
            .createCommandEncoder()
            .transientMemory()
            .uploadGpu(data, Integer.BYTES, GpuBuffer.USAGE_INDIRECT_PARAMETERS);
    context.getPass().drawIndexedIndirect(commands, count);
    ci.cancel();
  }
}
