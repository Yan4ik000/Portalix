package dev.portalix.client.mixin.gpu;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({GlobalSettingsUniform.class, ProjectionMatrixBuffer.class})
abstract class UniformUploadMixin {
  @WrapOperation(
      method = {"update", "writeBuffer"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/mojang/blaze3d/systems/CommandEncoder;writeToBuffer(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Ljava/nio/ByteBuffer;)V"))
  private void portalix$queuedUpload(
      CommandEncoder encoder,
      GpuBufferSlice destination,
      ByteBuffer data,
      Operation<Void> original) {

    var staging = encoder.transientMemory().uploadStaging(data, 4, GpuBuffer.USAGE_COPY_SRC);
    encoder.copyToBuffer(staging, destination);
  }
}
