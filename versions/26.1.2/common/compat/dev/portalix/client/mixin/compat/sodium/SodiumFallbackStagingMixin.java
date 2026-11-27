package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.nio.ByteBuffer;
import net.caffeinemc.mods.sodium.client.gl.arena.staging.FallbackStagingBuffer;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlBufferMapFlags;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlBufferUsage;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlMutableBuffer;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.util.EnumBitField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = FallbackStagingBuffer.class, remap = false)
abstract class SodiumFallbackStagingMixin {
  @Unique private static final EnumBitField<GlBufferMapFlags> portalix$WRITE =
      EnumBitField.of(GlBufferMapFlags.WRITE);

  @WrapOperation(method = "enqueueCopy", at = @At(value = "INVOKE", target =
      "Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;uploadData(Lnet/caffeinemc/mods/sodium/client/gl/buffer/GlMutableBuffer;Ljava/nio/ByteBuffer;Lnet/caffeinemc/mods/sodium/client/gl/buffer/GlBufferUsage;)V"), allow = 1)
  private void portalix$uploadHeapIndices(
      CommandList commands, GlMutableBuffer buffer, ByteBuffer data, GlBufferUsage usage,
      Operation<Void> original) {
    if (data.isDirect()) {
      original.call(commands, buffer, data, usage);
      return;
    }
    int size = data.remaining();
    commands.allocateStorage(buffer, size, usage);
    if (size == 0) return;
    var mapping = commands.mapBuffer(buffer, 0, size, portalix$WRITE);
    try {
      mapping.write(data, 0);
    } finally {
      commands.unmap(mapping);
    }
  }
}
