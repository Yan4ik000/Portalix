package dev.portalix.client.mixin.compat.sodium;

import java.nio.ByteBuffer;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlBufferMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GlBufferMapping.class, remap = false)
abstract class SodiumMappedBufferMixin {
  @Shadow public abstract ByteBuffer getMemoryBuffer();

  @Inject(method = "write", at = @At("HEAD"), cancellable = true, allow = 1)
  private void portalix$writeHeapIndices(ByteBuffer data, int writeOffset, CallbackInfo ci) {
    if (data.isDirect()) return;
    getMemoryBuffer().put(writeOffset, data, data.position(), data.remaining());
    ci.cancel();
  }
}
